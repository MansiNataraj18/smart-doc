import { useRef, useState } from "react";
import { uploadDocuments, deleteDocument } from "../api";
import ConfirmDialog from "./ConfirmDialog";

// This component is the "Upload Documents" section. Its only job is
// UI: show which files are picked, show progress, show success/error
// messages, and show the "replace?" / "delete?" popups.
//
// It does a quick, friendly check on the file name/type just so the
// user gets instant feedback instead of waiting for a round trip to
// the server. That check is NOT the real security check -- it's easy
// to fool (just rename any file to end in ".pdf"). The backend
// (DocumentService.isPdfFile) always re-checks the file's actual
// bytes before doing anything with it, so it never trusts this quick
// frontend check alone.
//
// It also does NOT decide whether a file is a duplicate. That's a
// business decision, so the backend (DocumentController) makes it.
// This component just sends files to the backend and displays
// whatever the backend says happened.
//
// "uploadedDocuments" is the persisted list (from GET /documents,
// backed by PostgreSQL) passed down from App.jsx. After an upload or
// delete, this component calls onDocumentsUploaded() so App.jsx
// re-fetches the real list from the backend, instead of guessing
// what changed.

function UploadPanel({ uploadedDocuments, onDocumentsUploaded }) {
  // The files the user has picked but not uploaded yet
  const [selectedFiles, setSelectedFiles] = useState([]);

  // True while an upload request is in flight
  const [isUploading, setIsUploading] = useState(false);

  // The message shown at the bottom of the upload box, e.g.
  // { type: "success" | "error", message: "..." }
  const [status, setStatus] = useState(null);

  // Names of files the backend told us already exist. Non-empty
  // means we show the "replace it?" popup.
  const [duplicateNames, setDuplicateNames] = useState([]);

  // Progress while uploading, e.g. { completed: 2, total: 5 }
  const [uploadProgress, setUploadProgress] = useState(null);

  // Name of a document the user clicked the delete (×) button on.
  // We wait for the user to confirm before actually deleting it.
  const [documentToDelete, setDocumentToDelete] = useState(null);

  // True while a delete request is in flight
  const [isDeleting, setIsDeleting] = useState(false);

  // Lets us clear the native file input after an upload
  const fileInputRef = useRef(null);

  // Called when the user picks files with the file dialog.
  //
  // We do a quick check here -- by file name/type -- just to give the
  // user instant feedback instead of making them wait for an upload
  // attempt to find out. This is ONLY a convenience: it's easy to
  // fake (rename any file to end in ".pdf"), so the backend always
  // does the real check on the file's actual content before storing
  // anything.
  function handleFileChange(event) {
    const chosenFiles = Array.from(event.target.files);

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

    // A fresh selection always needs to be re-checked by the
    // backend -- clear any stale duplicate/confirmation state left
    // over from a previous selection.
    setDuplicateNames([]);
  }

  // Clears the selected files and resets the file input, so the
  // upload box goes back to its empty state.
  function resetSelection() {
    setSelectedFiles([]);
    setDuplicateNames([]);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  // Removes one file from the pending selection before uploading.
  // Nothing is sent to the backend here -- this only changes what's
  // shown on screen.
  function handleRemovePendingFile(indexToRemove) {
    setSelectedFiles((previousFiles) =>
      previousFiles.filter((_, index) => index !== indexToRemove)
    );
  }

  // Uploads a list of files, one at a time, and shows a progress
  // message as each one finishes. "replace" tells the backend the
  // user has confirmed it's OK to overwrite an existing document.
  //
  // For every file, the backend sends back a status: "success",
  // "duplicate", "invalid" (not a real PDF), or "error". This
  // function just collects those results and turns them into what
  // the user sees -- it doesn't make any of those decisions itself.
  async function performUpload(filesToUpload, replace) {
    setIsUploading(true);
    setStatus(null);

    const total = filesToUpload.length;
    setUploadProgress({ completed: 0, total });

    const allResults = [];

    for (let index = 0; index < filesToUpload.length; index++) {
      try {
        const resultsForThisFile = await uploadDocuments(
          [filesToUpload[index]],
          replace
        );
        allResults.push(...resultsForThisFile);
      } catch (error) {
        // The request itself failed (e.g. backend is down)
        allResults.push({
          fileName: filesToUpload[index].name,
          status: "error",
          message: "Could not reach the server.",
        });
        console.error("Failed to upload document:", error);
      }

      setUploadProgress({ completed: index + 1, total });
    }

    setUploadProgress(null);

    // Re-fetch the persisted list from the backend now that the
    // upload attempt is done, instead of guessing what got saved.
    if (onDocumentsUploaded) {
      await onDocumentsUploaded();
    }

    // Sort the backend's results into the three things we show on screen
    const duplicates = allResults.filter((result) => result.status === "duplicate");
    const failures = allResults.filter(
      (result) => result.status === "invalid" || result.status === "error"
    );
    const successes = allResults.filter((result) => result.status === "success");

    if (duplicates.length > 0) {
      // Ask the user whether to replace these specific files
      setDuplicateNames(duplicates.map((result) => result.fileName));
    } else {
      resetSelection();
    }

    if (failures.length > 0) {
      const details = failures
        .map((result) => `${result.fileName} (${result.message})`)
        .join("; ");

      setStatus({
        type: "error",
        message: `${failures.length} file(s) could not be uploaded: ${details}`,
      });
    } else if (successes.length > 0 && duplicates.length === 0) {
      setStatus({
        type: "success",
        message: `${successes.length} document(s) uploaded successfully.`,
      });
    }

    setIsUploading(false);
  }

  // Called when the user clicks the "Upload" button.
  function handleUploadClick() {
    if (selectedFiles.length === 0) {
      setStatus({
        type: "error",
        message: "Please select at least one PDF file before uploading.",
      });
      return;
    }

    performUpload(selectedFiles, false);
  }

  // "Cancel" on the replace popup -- don't replace anything, just
  // close the popup and clear the pending selection.
  function handleCancelReplace() {
    setDuplicateNames([]);
    resetSelection();
  }

  // "Replace" on the replace popup -- re-upload just the file(s) the
  // backend flagged as duplicates, this time with replace=true.
  function handleConfirmReplace() {
    const filesToReplace = selectedFiles.filter((file) =>
      duplicateNames.includes(file.name)
    );

    setDuplicateNames([]);
    performUpload(filesToReplace, true);
  }

  // Clicking the × next to an already-uploaded document just asks
  // for confirmation first -- nothing is deleted yet.
  function handleDeleteClick(documentName) {
    setDocumentToDelete(documentName);
  }

  // "No" -- close the confirmation, nothing is deleted.
  function handleCancelDelete() {
    setDocumentToDelete(null);
  }

  // "Yes" -- actually delete the document. The backend removes it
  // from both Qdrant and PostgreSQL. Only refresh the list if the
  // delete actually succeeded.
  async function handleConfirmDelete() {
    const nameToDelete = documentToDelete;
    setIsDeleting(true);

    try {
      await deleteDocument(nameToDelete);
      setDocumentToDelete(null);

      if (onDocumentsUploaded) {
        await onDocumentsUploaded();
      }

      setStatus({
        type: "success",
        message: `"${nameToDelete}" was deleted.`,
      });
    } catch (error) {
      console.error("Failed to delete document:", error);
      setDocumentToDelete(null);
      setStatus({
        type: "error",
        message: `Failed to delete "${nameToDelete}". Please try again.`,
      });
    } finally {
      setIsDeleting(false);
    }
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
            {isUploading
              ? uploadProgress
                ? `Uploading ${uploadProgress.completed} of ${uploadProgress.total}...`
                : "Uploading..."
              : "Upload"}
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
                <button
                  type="button"
                  className="document-remove-button"
                  onClick={() => handleDeleteClick(document.name)}
                  disabled={isDeleting}
                  title={`Delete ${document.name}`}
                  aria-label={`Delete ${document.name}`}
                >
                  ×
                </button>
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

      {documentToDelete && (
        <ConfirmDialog
          message={`Are you sure you want to delete "${documentToDelete}"? This cannot be undone.`}
          confirmLabel="Delete"
          cancelLabel="Cancel"
          onConfirm={handleConfirmDelete}
          onCancel={handleCancelDelete}
        />
      )}
    </div>
  );
}

export default UploadPanel;
