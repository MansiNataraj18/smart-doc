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

    // Accepts one OR MORE PDFs at once. Each PDF is processed
    // independently (extract -> chunk -> embed -> store), but they
    // all land in the same Qdrant collection. "documentName" on
    // each chunk is what keeps them distinguishable later.
    //
    // NEW: once a file's chunks are successfully stored in Qdrant, we
    // persist a small metadata row for it in PostgreSQL (name + when
    // uploaded). This is what lets the Documents page and the Chat
    // page's document selector survive a browser refresh or a
    // restart -- they now read from the database instead of
    // in-memory frontend state.
    //
    // If extraction/chunking/embedding/Qdrant storage fails for a
    // file, this method throws before reaching the metadata-save
    // step for that file, so nothing gets persisted for it -- exactly
    // the "don't record documents that failed to ingest" behavior
    // that was asked for.
    // "replace" is OPTIONAL and defaults to false. The frontend sets
    // it to true only after the user has confirmed, via the
    // "already exists, replace it?" dialog, that this upload should
    // replace an existing document with the same name. We don't need
    // it for the PostgreSQL metadata row -- saveOrUpdate() already
    // updates the existing row by documentName instead of creating a
    // duplicate, regardless of this flag. It's captured here so the
    // flag genuinely reaches the backend end-to-end (rather than
    // being a frontend-only illusion of a confirmation), and so the
    // response can tell the caller a replacement was acknowledged.
    // Actually removing the OLD chunks for this document from Qdrant
    // (so re-uploads don't leave duplicate vectors behind) is a
    // separate follow-up step -- not done here yet.
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

    // The persisted list of uploaded documents (from PostgreSQL, not
    // frontend state). The Documents page and the Chat page's
    // document selector both read from this.
    @GetMapping
    public List<DocumentEntity> listDocuments() {
        return documentMetadataService.listAll();
    }

    // Deletes an already-uploaded document completely: its chunks
    // and vectors in Qdrant AND its metadata row in PostgreSQL. This
    // is what powers the "X" next to a document in the Documents
    // page -- it's a real deletion, not just hiding the row in the
    // frontend, so a deleted document can never come back as a
    // retrieval result.
    //
    // Qdrant is deleted FIRST, then PostgreSQL. If the Qdrant delete
    // fails, we throw before touching PostgreSQL, so the document
    // stays fully intact (and still shown in the UI) rather than
    // ending up in a half-deleted state. If either step fails, the
    // caller gets a 500 response and the frontend keeps the document
    // in the list, exactly as required.
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

    @PostMapping("/test-embedding")
    public float[] testEmbedding(
            @RequestParam("text") String text) {

        return embeddingService.generateEmbedding(text);
    }

    // Temporary endpoint to test retrieval on its own, before we
    // wire in the LLM. Given a question, this returns the top
    // matching chunks straight from Qdrant -- no answer generation yet.
    // Uses RetrievedChunk (not the raw LangChain4j match objects) so
    // the JSON response actually shows the text/metadata/score --
    // EmbeddingMatch's getters don't follow Jackson's naming rules
    // and serialize to empty "{}" otherwise.
    @PostMapping("/search")
    public List<RetrievedChunk> search(
            @RequestParam("text") String text) {

        return retrievalService.searchWithDetails(text);
    }

    // The full RAG pipeline: embed the question, search Qdrant for
    // the most relevant chunks, then ask the chat model to write an
    // answer using only those chunks -- and return the source
    // citations (document/page/chunk) alongside the answer.
    //
    // "documents" is OPTIONAL. If the caller doesn't send it (exactly
    // like every request before this feature existed), it comes in
    // as null, RetrievalService treats that as "search everything",
    // and behavior is 100% unchanged. If the caller sends one or more
    // "documents" values, only chunks from those documents are
    // searched.
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
