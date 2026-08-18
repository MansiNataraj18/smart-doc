package com.example.smart_doc.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentEntity;
import com.example.smart_doc.repository.DocumentRepository;

/** Handles PostgreSQL persistence for uploaded-document metadata (name + upload time). */
@Service
public class DocumentMetadataService {

    private final DocumentRepository documentRepository;

    public DocumentMetadataService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /** Checks whether a document with this exact name has already been uploaded. */
    public boolean exists(String documentName) {
        return documentRepository.findByDocumentName(documentName).isPresent();
    }

    /** Saves a new document, or updates its timestamp if it already exists. */
    public DocumentEntity saveOrUpdate(String documentName) {

        DocumentEntity document = documentRepository
                .findByDocumentName(documentName)
                .orElseGet(() -> new DocumentEntity(documentName, null));

        document.setUploadedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    /** Returns every persisted document, most recently uploaded first. */
    public List<DocumentEntity> listAll() {
        return documentRepository.findAllByOrderByUploadedAtDesc();
    }

    /** Removes the metadata row for one document, if it exists. */
    public void deleteByDocumentName(String documentName) {
        documentRepository.findByDocumentName(documentName)
                .ifPresent(documentRepository::delete);
    }
}
