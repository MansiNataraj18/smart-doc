package com.example.smart_doc.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.smart_doc.model.PageContent;

/**
 * Extracts text from an uploaded PDF, one page at a time.
 *
 * Each page's text is now made up of TWO things joined together:
 * 1. The PDF's normal text (whatever PDFBox can read directly)
 * 2. OCR text read from any images on that page (via {@link OcrService})
 *
 * Everything downstream (chunking, embeddings, Qdrant storage) just
 * sees one block of text per page -- it has no idea whether that text
 * came from normal extraction or OCR, so none of it needed to change.
 */
@Service
public class DocumentService {

    /** Every real PDF file starts with these 5 bytes. */
    private static final String PDF_SIGNATURE = "%PDF-";

    private final OcrService ocrService;

    public DocumentService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

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

    /**
     * Reads a PDF and returns its combined text (normal text + OCR text
     * from any images), one {@link PageContent} per page.
     */
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

                // Also run OCR on any images on this page (a scanned
                // page, a pasted screenshot, a diagram, etc.) and add
                // whatever text is found onto the page's normal text.
                PDPage page = document.getPage(pageNumber - 1);
                String ocrText = extractOcrTextFromPage(page);

                pageContent.setText(pageText + "\n" + ocrText);
                pageContents.add(pageContent);

            }
            return pageContents;

        } catch (IOException e) {
            throw new RuntimeException("Failed to process PDF", e);
        }
    }

    /** Runs OCR on every image on this page and joins the results into one block of text. */
    private String extractOcrTextFromPage(PDPage page) {

        StringBuilder ocrText = new StringBuilder();

        for (BufferedImage image : ocrService.extractImages(page)) {
            ocrText.append(ocrService.runOcr(image));
            ocrText.append("\n");
        }

        return ocrText.toString();
    }
}