package com.example.smart_doc.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping; 

import com.example.smart_doc.service.DocumentService;
import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.PageContent;
import com.example.smart_doc.service.ChunkingService;


@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ChunkingService chunkingService;

    public DocumentController(DocumentService documentService, ChunkingService chunkingService) {
        this.documentService = documentService;
        this.chunkingService = chunkingService;
    }

    @PostMapping("/upload")
    public List<DocumentChunk> getDocuments(@RequestParam("file") MultipartFile file) {
        List<PageContent> pageContents = documentService.processDocument(file);
        return chunkingService.chunkDocument(pageContents, file.getOriginalFilename());
    }
}
