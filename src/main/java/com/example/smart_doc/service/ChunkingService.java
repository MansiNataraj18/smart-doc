package com.example.smart_doc.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.PageContent;

/** Splits extracted page text into smaller, overlapping chunks. */
@Service
public class ChunkingService {

    /** Max characters per chunk. */
    private static final int CHUNK_SIZE = 500;

    /** Characters each chunk shares with the one before it. */
    private static final int OVERLAP = 100;

    /** Splits every page's text into overlapping chunks. */
    public List<DocumentChunk> chunkDocument(List<PageContent> pageContents, String documentName) {

        List<DocumentChunk> chunkedContents = new ArrayList<>();

        int chunkIndex = 0;

        for (PageContent pageContent : pageContents) {

            String text = pageContent.getText();
            int pageNumber = pageContent.getPageNumber();

            int start = 0;

            while (start < text.length()) {

                int end = Math.min(start + CHUNK_SIZE, text.length());

                String chunkText = text.substring(start, end);

                DocumentChunk documentChunk = new DocumentChunk();

                documentChunk.setPageNumber(pageNumber);
                documentChunk.setChunkIndex(chunkIndex);
                documentChunk.setText(chunkText);
                documentChunk.setDocumentName(documentName);

                chunkedContents.add(documentChunk);

                chunkIndex++;

                if (end == text.length()) {
                    break;
                }

                start += CHUNK_SIZE - OVERLAP;
            }
        }

        return chunkedContents;
    }
}