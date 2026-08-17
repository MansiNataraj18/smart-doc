import { useState, useEffect, useCallback } from "react";
import Sidebar from "./components/Sidebar";
import ChatPage from "./pages/ChatPage";
import DocumentsPage from "./pages/DocumentsPage";
import { getDocuments } from "./api";
import "./App.css";

function App() {
  // Which page is currently shown. Plain React state is enough here
  // -- no need for a routing library for two pages.
  const [activePage, setActivePage] = useState("chat");

  // The list of uploaded documents: [{ name, status }]. This now
  // comes from the backend (GET /documents, backed by PostgreSQL)
  // instead of being built up only in memory -- so it survives a
  // browser refresh and a restart of either the frontend or backend.
  // It's still lifted up here (rather than living only inside
  // UploadPanel) so both the Documents page and the Chat page's
  // document selector can read the same list.
  const [uploadedDocuments, setUploadedDocuments] = useState([]);

  // Fetches the persisted list from the backend and reshapes it into
  // the { name, status } form the existing UI already renders, so
  // nothing downstream (UploadPanel, ChatPage) needs to change.
  const refreshDocuments = useCallback(async () => {
    try {
      const documents = await getDocuments();

      setUploadedDocuments(
        documents.map((document) => ({
          name: document.documentName,
          status: "Uploaded",
        }))
      );
    } catch (error) {
      console.error("Failed to load documents from the backend:", error);
    }
  }, []);

  // Load the persisted document list once when the app starts.
  useEffect(() => {
    refreshDocuments();
  }, [refreshDocuments]);

  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} onNavigate={setActivePage} />

      <div className="main-content">
        {activePage === "chat" ? (
          <ChatPage uploadedDocuments={uploadedDocuments} />
        ) : (
          <DocumentsPage
            uploadedDocuments={uploadedDocuments}
            onDocumentsUploaded={refreshDocuments}
          />
        )}
      </div>
    </div>
  );
}

export default App;
