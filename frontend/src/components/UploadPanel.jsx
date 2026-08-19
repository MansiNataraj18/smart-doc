import { useRef, useState } from "react";
import { uploadDocuments, deleteDocument } from "../api";
import ConfirmDialog from "./ConfirmDialog";

function UploadPanel({ uploadedDocuments, onDocumentsUploaded }) {
  //Use a variable called selectedFiles, initially make it empty, and use setSelectedFiles to change it. Whenever it is changed using the setter, React updates the UI accordingly.
  const [selectedFiles, setSelectedFiles] = useState([]);

  const [isUploading, setIsUploading] = useState(false);

  const [status, setStatus] = useState(null);

  const [duplicateNames, setDuplicateNames] = useState([]);

  const [uploadProgress, setUploadProgress] = useState(null);

  const [documentToDelete, setDocumentToDelete] = useState(null);

  const [isDeleting, setIsDeleting] = useState(false);

  const [isDraggingOver, setIsDraggingOver] = useState(false);

  const fileInputRef = useRef(null);

  function addSelectedFiles(chosenFiles) {
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

    setDuplicateNames([]);
  }

  function handleFileChange(event) {
    addSelectedFiles(Array.from(event.target.files));
  }

  function handleDragOver(event) {
    event.preventDefault();

    if (!isUploading) {
      setIsDraggingOver(true);
    }
  }

  function handleDragLeave() {
    setIsDraggingOver(false);
  }
  function handleDrop(event) {
    event.preventDefault();
    setIsDraggingOver(false);

    if (isUploading) {
      return;
    }

    addSelectedFiles(Array.from(event.dataTransfer.files));
  }

  function resetSelection() {
    setSelectedFiles([]);
    setDuplicateNames([]);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  function handleRemovePendingFile(indexToRemove) {
    setSelectedFiles((previousFiles) =>
      previousFiles.filter((_, index) => index !== indexToRemove)
    );
  }

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

    if (onDocumentsUploaded) {
      await onDocumentsUploaded();
    }

    const duplicates = allResults.filter((result) => result.status === "duplicate");
    const failures = allResults.filter(
      (result) => result.status === "invalid" || result.status === "error"
    );
    const successes = allResults.filter((result) => result.status === "success");

    if (duplicates.length > 0) {
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

  function handleCancelReplace() {
    setDuplicateNames([]);
    resetSelection();
  }

  function handleConfirmReplace() {
    const filesToReplace = selectedFiles.filter((file) =>
      duplicateNames.includes(file.name)
    );

    setDuplicateNames([]);
    performUpload(filesToReplace, true);
  }

  function handleDeleteClick(documentName) {
    setDocumentToDelete(documentName);
  }

  function handleCancelDelete() {
    setDocumentToDelete(null);
  }

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
        <p className="upload-ocr-warning">
          Warning: If your PDF contains images such as flowcharts or
          diagrams, OCR may not accurately understand the image
          structure or relationships. Results may be inaccurate.
        </p>

        <div
          className={`upload-dropzone${
            isDraggingOver ? " upload-dropzone-active" : ""
          }`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          <p className="upload-dropzone-text">
            Drag and drop PDF files here, or
          </p>

          <div className="upload-controls">
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
              //show or prefer pdf files only in the file chooser dialog. This is done by setting the accept attribute to ".pdf,application/pdf". The multiple attribute allows users to select multiple files at once.
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
