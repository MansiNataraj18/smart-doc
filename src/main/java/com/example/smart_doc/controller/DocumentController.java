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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.smart_doc.model.AnswerResponse;
import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.DocumentEntity;
import com.example.smart_doc.model.PageContent;
import com.example.smart_doc.model.RetrievedChunk;
import com.example.smart_doc.model.UploadResult;
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
     * Uploads and ingests one or more PDFs: validate, check for a
     * duplicate name, then extract/chunk/embed/store in Qdrant, and
     * finally save metadata in PostgreSQL.
     *
     * Every decision (is this really a PDF? does it already exist?
     * did it succeed?) is made HERE on the backend. The frontend does
     * not validate or decide anything -- it only shows whatever
     * {@link UploadResult} we return for each file.
     *
     * One file's problem never stops the others: each file gets its
     * own result, and a failure on file 2 still lets files 1 and 3
     * finish normally.
     */
    @PostMapping("/upload")
    public List<UploadResult> uploadDocuments(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {

        List<UploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            results.add(uploadSingleFile(file, replace));
        }

        return results;
    }

    /**
     * Catches a file that's bigger than the 10 MB limit configured in
     * application.properties. Without this, that would come back as a
     * raw framework error instead of the same friendly per-file
     * result the frontend already knows how to display.
     *
     * We don't know the offending file's name at this point (the
     * size check happens while reading the upload, before we can
     * inspect individual files), so the message just explains the
     * limit rather than naming a specific file.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public List<UploadResult> handleFileTooLarge() {
        return List.of(new UploadResult(
                "Unknown file",
                "error",
                "This file is too large. The maximum allowed size is 10 MB."
        ));
    }

    /** Validates, checks for duplicates, and ingests exactly one file. */
    private UploadResult uploadSingleFile(MultipartFile file, boolean replace) {

        String fileName = file.getOriginalFilename();

        // 1. Reject anything that isn't really a PDF -- checked from
        // the file's actual bytes, not its name or browser-reported type.
        if (!documentService.isPdfFile(file)) {
            return new UploadResult(
                    fileName,
                    "invalid",
                    "This file is not a valid PDF."
            );
        }

        // 2. If a document with this name already exists and the
        // caller hasn't confirmed a replace, stop here -- the
        // frontend will show a "replace it?" dialog and try again
        // with replace=true if the user confirms.
        boolean alreadyExists = documentMetadataService.exists(fileName);

        if (alreadyExists && !replace) {
            return new UploadResult(
                    fileName,
                    "duplicate",
                    "A document named \"" + fileName + "\" already exists."
            );
        }

        try {
            // 3. Extract text from PDF
            List<PageContent> pageContents =
                    documentService.processDocument(file);

            // 3b. Reject PDFs with nothing to actually search --
            // usually a scanned/image-only PDF. Without this check,
            // it would "succeed" but store zero chunks and zero
            // embeddings, silently.
            if (!documentService.hasExtractableText(pageContents)) {
                return new UploadResult(
                        fileName,
                        "error",
                        "This PDF has no extractable text (it may be a scanned or image-only PDF)."
                );
            }

            // 4. Split extracted text into chunks
            List<DocumentChunk> chunks =
                    chunkingService.chunkDocument(pageContents, fileName);

            // 5. Generate embeddings for every chunk
            embeddingService.generateEmbeddings(chunks);

            // 6. Store chunks + embeddings in Qdrant
            qdrantService.storeChunks(chunks);

        } catch (Exception exception) {
            return new UploadResult(
                    fileName,
                    "error",
                    "Failed to process \"" + fileName + "\": " + exception.getMessage()
            );
        }

        // 7. Only NOW that Qdrant ingestion has actually succeeded,
        // persist the document's metadata.
        //
        // This can fail independently of everything above (e.g.
        // PostgreSQL is briefly unreachable). If it does, the
        // document is still fully searchable in Qdrant -- it just
        // won't show up in the Documents list or be selectable as a
        // filter until this succeeds (on a re-upload, or once the
        // database is reachable again). We don't roll back the
        // Qdrant write and we don't fail the whole request for this
        // -- that would require a distributed transaction across two
        // different datastores, which is unnecessary complexity for
        // this project. We just surface it clearly instead of
        // staying silent about it.
        try {
            documentMetadataService.saveOrUpdate(fileName);
        } catch (Exception exception) {
            return new UploadResult(
                    fileName,
                    "success",
                    "Indexed, but not saved to the documents list: " + exception.getMessage()
            );
        }

        String message = (replace && alreadyExists)
                ? "Replaced successfully."
                : "Uploaded and stored successfully.";

        return new UploadResult(fileName, "success", message);
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
