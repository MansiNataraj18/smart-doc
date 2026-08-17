import UploadPanel from "../components/UploadPanel";

// A page dedicated entirely to document management/uploading.
// UploadPanel's upload logic is unchanged -- it just now receives
// the persisted documents list as a prop (read from the backend),
// and calls onDocumentsUploaded to ask App.jsx to re-fetch that list
// after a successful upload, instead of keeping its own local copy.

function DocumentsPage({ uploadedDocuments, onDocumentsUploaded }) {
  return (
    <div className="documents-page">
      <UploadPanel
        uploadedDocuments={uploadedDocuments}
        onDocumentsUploaded={onDocumentsUploaded}
      />
    </div>
  );
}

export default DocumentsPage;
