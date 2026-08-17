package com.example.smart_doc.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.smart_doc.model.AnswerResponse;
import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.DocumentEntity;
import com.example.smart_doc.model.PageContent;
import com.example.smart_doc.model.RetrievedChunk;
import com.example.smart_doc.service.AnswerService;
import com.example.smart_doc.service.ChunkingService;
import com.example.smart_doc.service.DocumentMetadataService;
import com.example.smart_doc.service.DocumentService;
import com.example.smart_doc.service.EmbeddingService;
import com.example.smart_doc.service.QdrantService;
import com.example.smart_doc.service.RetrievalService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

/** The single HTTP entry point into the SmartDoc backend: upload, list, delete, ask. */
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final RetrievalService retrievalService;
    private final AnswerService answerService;
    private final DocumentMetadataService documentMetadataService;

    public DocumentController(
            DocumentService documentService,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            QdrantService qdrantService,
            RetrievalService retrievalService,
            AnswerService answerService,
            DocumentMetadataService documentMetadataService) {

        this.documentService = documentService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.retrievalService = retrievalService;
        this.answerService = answerService;
        this.documentMetadataService = documentMetadataService;
    }

    /**
     * Uploads and ingests one or more PDFs: extract, chunk, embed, store in Qdrant,
     * then save metadata in PostgreSQL. {@code replace} just flags a confirmed re-upload.
     */
    @PostMapping("/upload")
    public String uploadDocuments(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {

        // Collects one warning per file ONLY if Qdrant ingestion
        // succeeded but saving the PostgreSQL metadata row afterward
        // failed -- see the comment further down for why this is
        // handled separately from ingestion failures.
        List<String> metadataWarnings = new ArrayList<>();

        for (MultipartFile file : files) {

            // 1. Extract text from PDF
            List<PageContent> pageContents =
                    documentService.processDocument(file);

            // 2. Split extracted text into chunks
            List<DocumentChunk> chunks =
                    chunkingService.chunkDocument(
                            pageContents,
                            file.getOriginalFilename()
                    );

            // 3. Generate embeddings for every chunk
            embeddingService.generateEmbeddings(chunks);

            // 4. Store chunks + embeddings in Qdrant
            qdrantService.storeChunks(chunks);

            // 5. Only NOW that Qdrant ingestion has actually
            // succeeded, persist the document's metadata.
            //
            // This can fail independently of everything above (e.g.
            // PostgreSQL is briefly unreachable). If it does, the
            // document is still fully searchable in Qdrant -- it
            // just won't show up in the Documents list or be
            // selectable as a filter until this succeeds (on a
            // re-upload, or once the database is reachable again).
            // We don't roll back the Qdrant write and we don't fail
            // the whole request for this -- that would require a
            // distributed transaction across two different
            // datastores, which is unnecessary complexity for this
            // project. We just surface it clearly instead of staying
            // silent about it.
            try {
                documentMetadataService.saveOrUpdate(file.getOriginalFilename());
            } catch (Exception exception) {
                metadataWarnings.add(
                        file.getOriginalFilename()
                                + " (indexed, but not saved to the documents list: "
                                + exception.getMessage() + ")"
                );
            }
        }

        String prefix = replace ? "Replace confirmed. " : "";

        if (metadataWarnings.isEmpty()) {
            return prefix + "Documents uploaded and stored successfully";
        }

        return prefix + "Documents uploaded and stored successfully. Warnings: "
                + String.join("; ", metadataWarnings);
    }

    /** Lists every persisted document, most recently uploaded first. */
    @GetMapping
    public List<DocumentEntity> listDocuments() {
        return documentMetadataService.listAll();
    }

    /** Deletes a document's chunks in Qdrant and its metadata row in PostgreSQL. */
    @DeleteMapping
    public String deleteDocument(
            @RequestParam("documentName") String documentName) {

        try {
            qdrantService.deleteByDocumentName(documentName);
            documentMetadataService.deleteByDocumentName(documentName);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete " + documentName + ": " + exception.getMessage()
            );
        }

        return "Document deleted successfully";
    }

    /** Debug endpoint: embeds text and returns the raw vector. */
    @PostMapping("/test-embedding")
    public float[] testEmbedding(
            @RequestParam("text") String text) {

        return embeddingService.generateEmbedding(text);
    }

    /** Debug endpoint: searches Qdrant and returns matching chunks, without generating an answer. */
    @PostMapping("/search")
    public List<RetrievedChunk> search(
            @RequestParam("text") String text) {

        return retrievalService.searchWithDetails(text);
    }

    /** Full RAG pipeline: retrieves relevant chunks, then generates a cited answer. */
    @PostMapping("/ask")
    public AnswerResponse ask(
            @RequestParam("text") String question,
            @RequestParam(value = "documents", required = false) List<String> selectedDocuments) {

        // 1. Retrieve the top matching chunks for this question,
        // optionally restricted to the selected documents
        List<EmbeddingMatch<TextSegment>> matches =
                retrievalService.search(question, selectedDocuments);

        // 2. Generate the answer + citations from those chunks
        return answerService.generateAnswer(
                question,
                matches
        );
    }
}
