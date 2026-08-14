package com.example.smart_doc.model;

import lombok.Data;

// This is what we hand back after searching Qdrant.
// It looks a lot like DocumentChunk, but it also carries a "score"
// (how close this chunk is to the question) and it does NOT carry
// the embedding itself -- we don't need the raw vector once search is done.

@Data
public class RetrievedChunk {

    private String documentName;
    private Integer pageNumber;
    private String section;
    private Integer chunkIndex;
    private String text;
    private double score;
}
