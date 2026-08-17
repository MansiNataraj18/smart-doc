import UploadPanel from "../components/UploadPanel";

// A page dedicated entirely to document management/uploading.
// UploadPanel itself is unchanged -- this just gives it its own
// page layout instead of sitting above the chat.

function DocumentsPage() {
  return (
    <div className="documents-page">
      <UploadPanel />
    </div>
  );
}

export default DocumentsPage;
