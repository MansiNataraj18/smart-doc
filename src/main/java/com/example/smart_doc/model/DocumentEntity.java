package com.example.smart_doc.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Data;
import lombok.NoArgsConstructor;

// This is the ONE new persistent thing this feature adds: a row per
// uploaded PDF, so the Documents page and the Chat page's document
// selector both have something to read that survives a page refresh
// or a restart. It intentionally does NOT store chunks, embeddings,
// or text -- that all still lives in Qdrant exactly as before. This
// table only answers "which documents exist", nothing more.

@Entity
@Table(
        name = "documents",
        uniqueConstraints = @UniqueConstraint(columnNames = "document_name")
)
@Data
@NoArgsConstructor
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_name", nullable = false, unique = true)
    private String documentName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public DocumentEntity(String documentName, LocalDateTime uploadedAt) {
        this.documentName = documentName;
        this.uploadedAt = uploadedAt;
    }
}
