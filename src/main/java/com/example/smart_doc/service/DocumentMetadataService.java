package com.example.smart_doc.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentEntity;
import com.example.smart_doc.repository.DocumentRepository;

// Handles PostgreSQL persistence for uploaded-document METADATA only
// (name + when it was uploaded). This is deliberately separate from
// DocumentService (which does PDFBox text extraction) -- one service,
// one job, matching how ChunkingService/EmbeddingService/QdrantService
// are already split up in this project.
//
// This is the "source of truth" the frontend now reads from, instead
// of keeping its own in-memory list.

@Service
public class DocumentMetadataService {

    private final DocumentRepository documentRepository;

    public DocumentMetadataService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // Called ONLY after a file has already been fully chunked,
    // embedded, and stored in Qdrant -- see DocumentController.
    // If this exact document name was uploaded before, we just
    // refresh its timestamp instead of creating a second row --
    // simple "re-upload = update" behavior, no duplicate-handling
    // machinery needed beyond that.
    public DocumentEntity saveOrUpdate(String documentName) {

        DocumentEntity document = documentRepository
                .findByDocumentName(documentName)
                .orElseGet(() -> new DocumentEntity(documentName, null));

        document.setUploadedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    public List<DocumentEntity> listAll() {
        return documentRepository.findAllByOrderByUploadedAtDesc();
    }

    // Removes the metadata row for one document, if it exists. Reuses
    // the same findByDocumentName() lookup saveOrUpdate() already
    // relies on, instead of adding a new repository method. Used by
    // DocumentController's DELETE endpoint, alongside
    // QdrantService.deleteByDocumentName(), so a deleted document
    // disappears from both PostgreSQL and Qdrant together.
    public void deleteByDocumentName(String documentName) {
        documentRepository.findByDocumentName(documentName)
                .ifPresent(documentRepository::delete);
    }
}
