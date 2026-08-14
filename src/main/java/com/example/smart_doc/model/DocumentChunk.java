package com.example.smart_doc.model;

import lombok.Data;

@Data
public class DocumentChunk {

    private String documentId;     
    private String documentName;   
    private Integer pageNumber;   
    private String section;        
    private Integer chunkIndex;   
    private String text;           
}