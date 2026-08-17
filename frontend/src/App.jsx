import { useState } from "react";
import Sidebar from "./components/Sidebar";
import ChatPage from "./pages/ChatPage";
import DocumentsPage from "./pages/DocumentsPage";
import "./App.css";

function App() {
  // Which page is currently shown. Plain React state is enough here
  // -- no need for a routing library for two pages.
  const [activePage, setActivePage] = useState("chat");

  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} onNavigate={setActivePage} />

      <div className="main-content">
        {activePage === "chat" ? <ChatPage /> : <DocumentsPage />}
      </div>
    </div>
  );
}

export default App;
