package com.example.smart_doc.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.smart_doc.model.PageContent;

/** Extracts text from an uploaded PDF, one page at a time. */
@Service
public class DocumentService {

    /** Every real PDF file starts with these 5 bytes. */
    private static final String PDF_SIGNATURE = "%PDF-";

    /**
     * Checks the file's actual bytes (not its name or the browser-reported
     * type, both of which are easy to fake) to confirm it's really a PDF.
     * This is the ONLY place PDF-ness is decided -- the frontend just
     * displays whatever this method (via the controller) reports back.
     */
    public boolean isPdfFile(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {

            byte[] header = new byte[PDF_SIGNATURE.length()];
            int bytesRead = inputStream.read(header);

            if (bytesRead < PDF_SIGNATURE.length()) {
                return false;
            }

            String signature = new String(header, StandardCharsets.US_ASCII);
            return signature.equals(PDF_SIGNATURE);

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * True if at least one page has some real (non-blank) text on it.
     *
     * A scanned/image-only PDF still passes the {@link #isPdfFile}
     * check (it's a real PDF) but has nothing for PDFBox to extract,
     * which would otherwise silently produce zero chunks and zero
     * embeddings -- an upload that "succeeds" but is not searchable.
     * This lets the caller catch that case and report it clearly
     * instead of staying silent about it.
     */
    public boolean hasExtractableText(List<PageContent> pageContents) {

        for (PageContent pageContent : pageContents) {

            String text = pageContent.getText();

            if (text != null && !text.isBlank()) {
                return true;
            }
        }

        return false;
    }

    /** Reads a PDF and returns its text, one {@link PageContent} per page. */
    public List<PageContent> processDocument(MultipartFile file) {

        List<PageContent> pageContents = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper pdfTextStripper = new PDFTextStripper();

            int numberOfPages = document.getNumberOfPages();

            for (int pageNumber = 1; pageNumber <= numberOfPages; pageNumber++) {

                PageContent pageContent = new PageContent();
                pageContent.setPageNumber(pageNumber);

                pdfTextStripper.setStartPage(pageNumber);
                pdfTextStripper.setEndPage(pageNumber);

                String pageText = pdfTextStripper.getText(document);
                pageContent.setText(pageText);
                pageContents.add(pageContent);

            }
            return pageContents;

        } catch (IOException e) {
            throw new RuntimeException("Failed to process PDF", e);
        }
    }
}