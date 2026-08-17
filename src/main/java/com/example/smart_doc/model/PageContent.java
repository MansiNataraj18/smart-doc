package com.example.smart_doc.model;

import lombok.Data;

/** The extracted text of a single PDF page. */
@Data
public class PageContent {

    /** Page number within the PDF. */
    private int pageNumber;

    /** Text extracted from that page. */
    private String text;
}
