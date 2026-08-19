package com.example.smart_doc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentChunk;
import com.example.smart_doc.model.PageContent;

/**
 * Splits extracted page text into smaller, overlapping chunks.
 *
 * Chunks are built along natural text boundaries -- paragraphs first,
 * falling back to sentences, falling back to a hard character cut only
 * as a last resort -- instead of blindly slicing every N characters.
 * This avoids cutting a chunk off mid-sentence, which used to hurt how
 * well a chunk's embedding represented its actual meaning.
 *
 * Paragraph boundaries come from {@link DocumentService}, which marks
 * them (via PDFTextStripper) as a blank line in the extracted text.
 */
@Service
public class ChunkingService {

    /** Target max characters per chunk. */
    private static final int CHUNK_SIZE = 500;

    /** Roughly how much of the previous chunk's end carries into the next one. */
    private static final int OVERLAP = 100;

    /** A blank line (one or more) is how DocumentService marks a paragraph break. */
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n+");

    /** Splits after a sentence-ending punctuation mark followed by whitespace. */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    /** Splits every page's text into overlapping, boundary-aware chunks. */
    public List<DocumentChunk> chunkDocument(List<PageContent> pageContents, String documentName) {

        List<DocumentChunk> chunkedContents = new ArrayList<>();

        int chunkIndex = 0;

        for (PageContent pageContent : pageContents) {

            int pageNumber = pageContent.getPageNumber();
            List<String> segments = splitIntoSegments(pageContent.getText());

            List<String> currentSegments = new ArrayList<>();
            int currentLength = 0;

            for (String segment : segments) {

                if (currentLength > 0 && currentLength + segment.length() > CHUNK_SIZE) {

                    chunkIndex = emitChunk(chunkedContents, currentSegments, pageNumber, chunkIndex, documentName);

                    currentSegments = carryOverTail(currentSegments);
                    currentLength = totalLength(currentSegments);
                }

                currentSegments.add(segment);
                currentLength += segment.length();
            }

            if (!currentSegments.isEmpty()) {
                chunkIndex = emitChunk(chunkedContents, currentSegments, pageNumber, chunkIndex, documentName);
            }
        }

        return chunkedContents;
    }

    /**
     * Breaks a page's text into pieces no larger than {@link #CHUNK_SIZE},
     * preferring to split on paragraph breaks, then sentence breaks, and
     * only falling back to a hard character cut if a single sentence is
     * still too long to fit in one chunk on its own (rare).
     */
    private List<String> splitIntoSegments(String text) {

        List<String> segments = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return segments;
        }

        for (String paragraph : PARAGRAPH_SPLIT.split(text.trim())) {

            paragraph = paragraph.trim();

            if (paragraph.isEmpty()) {
                continue;
            }

            if (paragraph.length() <= CHUNK_SIZE) {
                segments.add(paragraph);
                continue;
            }

            // Paragraph itself is too long -- split it into sentences instead.
            for (String sentence : SENTENCE_SPLIT.split(paragraph)) {

                sentence = sentence.trim();

                if (sentence.isEmpty()) {
                    continue;
                }

                if (sentence.length() <= CHUNK_SIZE) {
                    segments.add(sentence);
                } else {
                    // Even one sentence is too long -- hard-cut it as a
                    // last resort so nothing gets silently dropped.
                    segments.addAll(hardSplit(sentence));
                }
            }
        }

        return segments;
    }

    /** Last-resort fixed-size split, only used if a single sentence exceeds CHUNK_SIZE. */
    private List<String> hardSplit(String text) {

        List<String> parts = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            parts.add(text.substring(start, end));
            start = end;
        }

        return parts;
    }

    /** Joins the current chunk's segments into one DocumentChunk and stores it. */
    private int emitChunk(List<DocumentChunk> chunkedContents, List<String> segments,
                           int pageNumber, int chunkIndex, String documentName) {

        DocumentChunk documentChunk = new DocumentChunk();
        documentChunk.setPageNumber(pageNumber);
        documentChunk.setChunkIndex(chunkIndex);
        documentChunk.setText(String.join(" ", segments));
        documentChunk.setDocumentName(documentName);

        chunkedContents.add(documentChunk);

        return chunkIndex + 1;
    }

    /**
     * Picks whichever trailing segments from the just-finished chunk add
     * up to roughly {@link #OVERLAP} characters, so the next chunk starts
     * with real context instead of an arbitrary character cut.
     */
    private List<String> carryOverTail(List<String> segments) {

        List<String> tail = new ArrayList<>();
        int length = 0;

        for (int i = segments.size() - 1; i >= 0; i--) {

            String segment = segments.get(i);

            if (length > 0 && length + segment.length() > OVERLAP) {
                break;
            }

            tail.add(0, segment);
            length += segment.length();
        }

        return tail;
    }

    private int totalLength(List<String> segments) {
        int length = 0;
        for (String segment : segments) {
            length += segment.length();
        }
        return length;
    }
}
