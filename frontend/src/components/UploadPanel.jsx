import { useRef, useState } from "react";
import { uploadDocuments } from "../api";

// A self-contained "Upload Documents" section. It manages its own
// state only (selected files, loading, status message, and the list
// of documents uploaded so far) and does not touch anything related
// to the chat -- it just calls the existing /documents/upload
// endpoint through api.js.
//
// Note: "uploadedDocuments" below is NOT backed by any backend list
// endpoint (there isn't one yet). It only remembers what was
// uploaded during this browser session -- refreshing the page clears
// it, even though the documents themselves are still safely stored
// in Qdrant on the backend.

function UploadPanel() {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [isUploading, setIsUploading] = useState(false);

  // status: { type: "success" | "error", message: string } | null
  const [status, setStatus] = useState(null);

  // Documents uploaded so far THIS SESSION: [{ name, status }]
  const [uploadedDocuments, setUploadedDocuments] = useState([]);

  const fileInputRef = useRef(null);

  function handleFileChange(event) {
    const chosenFiles = Array.from(event.target.files);

    // Only allow PDFs, even though the file picker is already
    // filtered to PDFs -- some browsers/OSes let users override that.
    const pdfFiles = chosenFiles.filter(
      (file) =>
        file.type === "application/pdf" ||
        file.name.toLowerCase().endsWith(".pdf")
    );

    if (pdfFiles.length < chosenFiles.length) {
      setStatus({
        type: "error",
        message: "Only PDF files are allowed. Non-PDF files were ignored.",
      });
    } else {
      setStatus(null);
    }

    setSelectedFiles(pdfFiles);
  }

  function resetSelection() {
    setSelectedFiles([]);

    // Clears the native file input too, so the same file can be
    // re-selected later if needed.
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  async function handleUpload() {
    if (selectedFiles.length === 0) {
      setStatus({
        type: "error",
        message: "Please select at least one PDF file before uploading.",
      });
      return;
    }

    setIsUploading(true);
    setStatus(null);

    try {
      await uploadDocuments(selectedFiles);

      setStatus({
        type: "success",
        message: `${selectedFiles.length} document(s) uploaded successfully.`,
      });

      // Add these files to the "Uploaded Documents" list now that we
      // know the upload actually succeeded.
      const newlyUploaded = selectedFiles.map((file) => ({
        name: file.name,
        status: "Uploaded",
      }));

      setUploadedDocuments((previous) => [...previous, ...newlyUploaded]);

      resetSelection();
    } catch (error) {
      setStatus({
        type: "error",
        message:
          "Upload failed. Please check that the backend is running and try again.",
      });
      console.error("Failed to upload documents:", error);
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <div className="documents-content">
      <section className="upload-panel">
        <h2 className="upload-title">Upload Documents</h2>

        <div className="upload-controls">
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,application/pdf"
            multiple
            onChange={handleFileChange}
            disabled={isUploading}
            className="upload-file-input"
          />

          <button
            className="upload-button"
            onClick={handleUpload}
            disabled={isUploading || selectedFiles.length === 0}
          >
            {isUploading ? "Uploading..." : "Upload"}
          </button>
        </div>

        {selectedFiles.length > 0 && (
          <ul className="upload-file-list">
            {selectedFiles.map((file, index) => (
              <li key={index}>{file.name}</li>
            ))}
          </ul>
        )}

        {status && (
          <div
            className={`upload-status ${
              status.type === "success" ? "upload-success" : "upload-error"
            }`}
          >
            {status.message}
          </div>
        )}
      </section>

      <section className="documents-list-panel">
        <h2 className="documents-list-title">Uploaded Documents</h2>

        {uploadedDocuments.length === 0 ? (
          <div className="documents-list-empty">
            No documents uploaded yet in this session.
          </div>
        ) : (
          <ul className="document-card-list">
            {uploadedDocuments.map((document, index) => (
              <li className="document-card" key={index}>
                <span className="document-icon">📄</span>
                <span className="document-name">{document.name}</span>
                <span className="document-status">{document.status}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export default UploadPanel;
