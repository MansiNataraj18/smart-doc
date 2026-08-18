package com.example.smart_doc.model;

/**
 * What happened when the backend tried to upload one file.
 *
 * status is one of:
 *   "success"   - the file was validated, processed, and stored
 *   "duplicate" - a document with this name already exists, and the
 *                 caller did not confirm a replace
 *   "invalid"   - the file is not actually a PDF
 *   "error"     - something went wrong while processing the file
 *
 * The frontend does not decide any of this -- it just displays
 * whichever status/message the backend sends back.
 */
public class UploadResult {

    private String fileName;
    private String status;
    private String message;

    public UploadResult(String fileName, String status, String message) {
        this.fileName = fileName;
        this.status = status;
        this.message = message;
    }

    /** @return the uploaded file's name */
    public String getFileName() {
        return fileName;
    }

    /** @return "success", "duplicate", "invalid", or "error" */
    public String getStatus() {
        return status;
    }

    /** @return a human-readable explanation of the status */
    public String getMessage() {
        return message;
    }
}
