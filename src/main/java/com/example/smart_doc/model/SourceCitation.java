package com.example.smart_doc.model;

/** One citation shown under an answer: "this chunk of this document was used". */
public class SourceCitation {

    private String document;
    private int page;
    private int chunk;

    /** Creates a citation pointing at one chunk of one document. */
    public SourceCitation(String document, int page, int chunk) {
        this.document = document;
        this.page = page;
        this.chunk = chunk;
    }

    /** @return the source document's name */
    public String getDocument() {
        return document;
    }

    /** @return the page number */
    public int getPage() {
        return page;
    }

    /** @return the chunk index */
    public int getChunk() {
        return chunk;
    }
}
