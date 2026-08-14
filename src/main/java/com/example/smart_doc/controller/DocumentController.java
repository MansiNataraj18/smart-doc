package com.example.smart_doc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import com.example.smart_doc.model.AnswerResponse;
import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.PageContent;
import com.example.smart_doc.service.AnswerService;
import com.example.smart_doc.service.ChunkingService;
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

    public DocumentController(
            DocumentService documentService,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            QdrantService qdrantService,
            RetrievalService retrievalService,
            AnswerService answerService) {

        this.documentService = documentService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.retrievalService = retrievalService;
        this.answerService = answerService;
    }

    @PostMapping("/upload")
    public String uploadDocument(
            @RequestParam("file") MultipartFile file) {

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

        return "Document uploaded and stored successfully";
    }

    @PostMapping("/test-embedding")
    public float[] testEmbedding(
            @RequestParam("text") String text) {

        return embeddingService.generateEmbedding(text);
    }

    // Temporary endpoint to test retrieval on its own, before we
    // wire in the LLM. Given a question, this returns the top
    // matching chunks straight from Qdrant -- no answer generation yet.
    @PostMapping("/search")
    public List<EmbeddingMatch<TextSegment>> search(
            @RequestParam("text") String text) {

        return retrievalService.search(text);
    }

    // The full RAG pipeline: embed the question, search Qdrant for
    // the most relevant chunks, then ask the chat model to write an
    // answer using only those chunks -- and return the source
    // citations (document/page/chunk) alongside the answer.
    @PostMapping("/ask")
    public AnswerResponse ask(
            @RequestParam("text") String question) {

        // 1. Retrieve the top matching chunks for this question
        List<EmbeddingMatch<TextSegment>> matches =
                retrievalService.search(question);

        // 2. Generate the answer + citations from those chunks
        return answerService.generateAnswer(
                question,
                matches
        );
    }
}
