package com.example.smart_doc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.PageContent;
import com.example.smart_doc.service.ChunkingService;
import com.example.smart_doc.service.DocumentService;
import com.example.smart_doc.service.EmbeddingService;
import com.example.smart_doc.service.QdrantService;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;

    public DocumentController(
            DocumentService documentService,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            QdrantService qdrantService) {

        this.documentService = documentService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
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
}