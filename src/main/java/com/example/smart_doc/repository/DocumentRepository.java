package com.example.smart_doc.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.smart_doc.model.DocumentEntity;

/** Spring Data JPA repository for {@link DocumentEntity}; SQL is generated from method names. */
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    /** Looks up a document by its exact filename. */
    Optional<DocumentEntity> findByDocumentName(String documentName);

    /** Returns every document, most recently uploaded first. */
    List<DocumentEntity> findAllByOrderByUploadedAtDesc();
}
