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

/** A "this document exists" row in PostgreSQL -- name + upload time only, no chunks/text. */
@Entity
@Table(
        name = "documents",
        uniqueConstraints = @UniqueConstraint(columnNames = "document_name")
)
@Data
@NoArgsConstructor
public class DocumentEntity {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The uploaded file's name; unique so re-uploads update, not duplicate. */
    @Column(name = "document_name", nullable = false, unique = true)
    private String documentName;

    /** When this document was last (re)uploaded. */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /** Creates a new document record. */
    public DocumentEntity(String documentName, LocalDateTime uploadedAt) {
        this.documentName = documentName;
        this.uploadedAt = uploadedAt;
    }
}
