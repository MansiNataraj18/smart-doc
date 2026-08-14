package com.example.smart_doc.model;

// This is what we show under an answer, so the user can verify it
// themselves. One SourceCitation = "this chunk of this document
// helped produce this answer".

public class SourceCitation {

    private String document;
    private int page;
    private int chunk;

    public SourceCitation(String document, int page, int chunk) {
        this.document = document;
        this.page = page;
        this.chunk = chunk;
    }

    public String getDocument() {
        return document;
    }

    public int getPage() {
        return page;
    }

    public int getChunk() {
        return chunk;
    }
}
