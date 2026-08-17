package com.example.smart_doc.service;

import java.io.IOException;
import java.io.InputStream;
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