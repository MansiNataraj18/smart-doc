package com.example.smart_doc.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.smart_doc.model.DocumentEntity;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    // Used to detect "this document was already uploaded before" so
    // we can update it instead of creating a duplicate row.
    Optional<DocumentEntity> findByDocumentName(String documentName);

    // Newest first, so the Documents page shows recently uploaded
    // files at the top.
    List<DocumentEntity> findAllByOrderByUploadedAtDesc();
}
