package com.example.smart_doc.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Runs OCR (Optical Character Recognition) on images found inside a
 * PDF page, so text inside scanned pages, screenshots, or diagrams
 * can be picked up too -- not just text PDFBox can already read
 * directly from the page.
 *
 * This is intentionally simple, on purpose:
 * - it only looks at images placed directly on the page (not images
 *   nested inside other embedded objects), which covers the common
 *   cases (a scanned page, a pasted screenshot, an embedded diagram).
 * - it does NOT try to understand what a diagram/flowchart means --
 *   it just reads whatever words Tesseract can recognize in it, the
 *   same way it would read a screenshot of a paragraph of text.
 *
 * IMPORTANT: this only works once Tesseract itself (the actual OCR
 * program, separate from this Java library) is installed on the
 * machine running the backend -- e.g. `brew install tesseract` on
 * macOS. See the README for setup details.
 */
@Service
public class OcrService {

    // Where Tesseract's language data files ("tessdata") live on
    // disk. Configurable via application.properties (ocr.tessdata-path)
    // since this differs by operating system / install method.
    private final String tessDataPath;

    public OcrService(
            @Value("${ocr.tessdata-path:/usr/share/tesseract-ocr/5/tessdata}") String tessDataPath,
            @Value("${ocr.native-library-path:}") String nativeLibraryPath) {

        this.tessDataPath = tessDataPath;

        // Tess4J needs the actual Tesseract program (a native library,
        // not something Maven can download) to already be installed on
        // this machine. If it's not somewhere Java looks by default,
        // this tells it exactly where to find it. Left blank (the
        // default), this does nothing -- only set ocr.native-library-path
        // if OCR fails with an "Unable to load library 'tesseract'" error.
        if (!nativeLibraryPath.isBlank()) {
            System.setProperty("jna.library.path", nativeLibraryPath);
        }
    }

    /**
     * Finds every image placed directly on this page.
     * Returns an empty list (never throws) if none are found or if
     * they can't be read -- OCR is a bonus on top of the PDF's normal
     * text, never something that should stop the upload.
     */
    public List<BufferedImage> extractImages(PDPage page) {

        List<BufferedImage> images = new ArrayList<>();

        try {
            for (COSName name : page.getResources().getXObjectNames()) {

                PDXObject xObject = page.getResources().getXObject(name);

                if (xObject instanceof PDImageXObject imageXObject) {
                    images.add(imageXObject.getImage());
                }
            }
        } catch (IOException e) {
            // Couldn't read this page's images -- just skip OCR for
            // this page. The page's normal PDFBox text still applies.
        }

        return images;
    }

    /**
     * Runs OCR on one image and returns whatever text Tesseract finds.
     * Returns an empty string (never throws) if OCR fails on this
     * image, so one bad/unreadable image can't break the whole upload.
     */
    public String runOcr(BufferedImage image) {

        // A fresh Tesseract instance per call -- Tesseract is NOT
        // safe to share across concurrent uploads, and creating a new
        // one is cheap enough for this use case.
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);

        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            return "";
        }
    }
}
