import { useRef, useState } from "react";
import { uploadDocuments } from "../api";
import ConfirmDialog from "./ConfirmDialog";

// A self-contained "Upload Documents" section. It manages its own
// state for the upload flow (selected files, loading, status
// message, and now the duplicate-confirmation step) and does not
// touch anything related to the chat -- it just calls the existing
// /documents/upload endpoint through api.js.
//
// "uploadedDocuments" is the persisted list (from GET /documents,
// backed by PostgreSQL) passed down from App.jsx, so the Chat page
// can also see which documents are available to filter by. After a
// successful upload, this component calls onDocumentsUploaded()
// (rather than editing the list itself) so App.jsx re-fetches the
// real list from the backend -- keeping the database the single
// source of truth instead of guessing what got saved.
//
// This same "uploadedDocuments" list is also what we use to detect
// duplicate filenames before uploading -- no new endpoint needed,
// just reusing the list that's already fetched.

function UploadPanel({ uploadedDocuments, onDocumentsUploaded }) {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [isUploading, setIsUploading] = useState(false);

  // status: { type: "success" | "error", message: string } | null
  const [status, setStatus] = useState(null);

  // Filenames from the CURRENT selection that already exist in
  // uploadedDocuments. Non-empty means the confirm dialog is shown
  // before uploading.
  const [duplicateNames, setDuplicateNames] = useState([]);

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
    // A fresh selection always needs to be re-checked -- clear any
    // stale duplicate/confirmation state from a previous selection.
    setDuplicateNames([]);
  }

  function resetSelection() {
    setSelectedFiles([]);
    setDuplicateNames([]);

    // Clears the native file input too, so the same file can be
    // re-selected later if needed.
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  // Removes ONE file from the current pending selection (before
  // anything is uploaded) -- e.g. the user picked the wrong PDF and
  // wants to drop just that one without clearing the whole
  // selection. This only touches local component state: nothing is
  // sent to the backend, so PostgreSQL/Qdrant are never involved,
  // and it has no effect on the duplicate-warning flow (that's only
  // triggered later, on the Upload button click).
  function handleRemovePendingFile(indexToRemove) {
    setSelectedFiles((previousFiles) =>
      previousFiles.filter((_, index) => index !== indexToRemove)
    );
  }

  // Actually sends the upload request. "replace" tells the backend
  // this was confirmed by the user as an intentional replacement of
  // an existing document (see api.js / DocumentController).
  async function performUpload(replace) {
    setIsUploading(true);
    setStatus(null);

    try {
      await uploadDocuments(selectedFiles, replace);

      setStatus({
        type: "success",
        message: `${selectedFiles.length} document(s) uploaded successfully.`,
      });

      // Re-fetch the persisted list from the backend now that the
      // upload succeeded, instead of guessing what got saved --
      // this is what keeps PostgreSQL as the single source of truth.
      if (onDocumentsUploaded) {
        await onDocumentsUploaded();
      }

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

  function handleUploadClick() {
    if (selectedFiles.length === 0) {
      setStatus({
        type: "error",
        message: "Please select at least one PDF file before uploading.",
      });
      return;
    }

    // Compare the selected filenames against the already-persisted
    // documents list (from GET /documents / PostgreSQL) instead of
    // adding a new backend check -- the list we already have is the
    // right source of truth for "does this document already exist".
    const existingNames = new Set(
      (uploadedDocuments || []).map((document) => document.name)
    );

    const duplicates = selectedFiles
      .map((file) => file.name)
      .filter((name) => existingNames.has(name));

    if (duplicates.length > 0) {
      // Don't upload yet -- ask for confirmation first.
      setDuplicateNames(duplicates);
      return;
    }

    performUpload(false);
  }

  function handleCancelReplace() {
    // "Do not send the upload request. Keep the existing document
    // unchanged." -- close the dialog AND discard the pending
    // selection that triggered it (same cleanup as resetSelection:
    // clears selectedFiles, duplicateNames, and the native file
    // input), so the upload area immediately goes back to its empty
    // state instead of leaving the just-rejected file sitting there.
    resetSelection();
  }

  function handleConfirmReplace() {
    setDuplicateNames([]);
    performUpload(true);
  }

  const duplicateMessage =
    duplicateNames.length === 1
      ? `A file named "${duplicateNames[0]}" already exists. Do you want to replace it?`
      : `The following files already exist: ${duplicateNames.join(
          ", "
        )}. Do you want to replace them?`;

  return (
    <div className="documents-content">
      <section className="upload-panel">
        <h2 className="upload-title">Upload Documents</h2>

        <div className="upload-controls">
          {/* The native <input type="file"> shows its OWN chosen-file
              text right next to the button (that's browser chrome,
              not something we render) -- and there's no way to make
              it forget just one file when a single pending file is
              removed below. So instead of showing the raw input, we
              hide it and trigger it from our own "Choose Files"
              label. The list below (driven entirely by our
              "selectedFiles" state) becomes the ONE place selected
              files are shown, so there's no duplicate text and
              removing a file there is the only place removing it
              needs to happen. */}
          <label
            htmlFor="pdf-upload-input"
            className={`upload-choose-button${
              isUploading ? " upload-choose-button-disabled" : ""
            }`}
            onClick={(event) => {
              if (isUploading) {
                event.preventDefault();
              }
            }}
          >
            Choose Files
          </label>

          <input
            id="pdf-upload-input"
            ref={fileInputRef}
            type="file"
            accept=".pdf,application/pdf"
            multiple
            onChange={handleFileChange}
            disabled={isUploading}
            className="upload-file-input-hidden"
          />

          <button
            className="upload-button"
            onClick={handleUploadClick}
            disabled={isUploading || selectedFiles.length === 0}
          >
            {isUploading ? "Uploading..." : "Upload"}
          </button>
        </div>

        {selectedFiles.length > 0 && (
          <ul className="upload-file-list">
            {selectedFiles.map((file, index) => (
              <li key={index} className="upload-file-item">
                <span className="upload-file-name">{file.name}</span>
                <button
                  type="button"
                  className="upload-file-remove-button"
                  onClick={() => handleRemovePendingFile(index)}
                  disabled={isUploading}
                  title={`Remove ${file.name}`}
                  aria-label={`Remove ${file.name}`}
                >
                  ×
                </button>
              </li>
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

      {duplicateNames.length > 0 && (
        <ConfirmDialog
          message={duplicateMessage}
          confirmLabel="Replace"
          cancelLabel="Cancel"
          onConfirm={handleConfirmReplace}
          onCancel={handleCancelReplace}
        />
      )}
    </div>
  );
}

export default UploadPanel;
