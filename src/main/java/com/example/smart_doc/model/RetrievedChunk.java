package com.example.smart_doc.model;

import lombok.Data;

/** A single search result from Qdrant, shaped for JSON (used by the debug /search endpoint). */
@Data
public class RetrievedChunk {

    /** Name of the document this chunk came from. */
    private String documentName;

    /** Page number this chunk came from. */
    private Integer pageNumber;

    /** Optional section/heading label. */
    private String section;

    /** Position of this chunk within its document. */
    private Integer chunkIndex;

    /** The chunk's text. */
    private String text;

    /** Similarity score; higher means more relevant. */
    private double score;
}
