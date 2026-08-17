package com.example.smart_doc.model;

import lombok.Data;

/** One chunk of a document's text, on its way into Qdrant. */
@Data
public class DocumentChunk {

    /** Reserved for future use; not currently set. */
    private String documentId;

    /** Name of the PDF this chunk came from. */
    private String documentName;

    /** Page number this chunk came from. */
    private Integer pageNumber;

    /** Optional section/heading label. */
    private String section;

    /** Position of this chunk within its document. */
    private Integer chunkIndex;

    /** The chunk's text. */
    private String text;

    /** The chunk's embedding vector, once generated. */
    private float[] embedding;
}
