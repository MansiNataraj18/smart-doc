import { useState, useRef, useEffect, useMemo } from "react";
import ChatMessage from "../components/ChatMessage";
import ChatInput from "../components/ChatInput";
import { askQuestion } from "../api";

// This is the same chat logic that has always lived here, plus one
// addition: an OPTIONAL document filter. Selecting nothing still
// searches every document, exactly as before.

function ChatPage({ uploadedDocuments }) {
  // Every message we show on screen: { role: "user" | "assistant", text, sources? }
  const [messages, setMessages] = useState([]);

  // True while we're waiting for the backend to respond
  const [isLoading, setIsLoading] = useState(false);

  // Which document names the user has chosen to restrict the search
  // to. Empty array = no restriction = search everything.
  const [selectedDocuments, setSelectedDocuments] = useState([]);

  // Used to auto-scroll to the latest message
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  // uploadedDocuments can contain the same filename more than once
  // (e.g. the same PDF uploaded twice), so dedupe down to the
  // distinct names available to select from.
  const availableDocumentNames = useMemo(() => {
    const uniqueNames = new Set(
      (uploadedDocuments || []).map((document) => document.name)
    );
    return Array.from(uniqueNames);
  }, [uploadedDocuments]);

  function toggleDocumentSelection(name) {
    setSelectedDocuments((previous) =>
      previous.includes(name)
        ? previous.filter((selectedName) => selectedName !== name)
        : [...previous, name]
    );
  }

  function clearDocumentSelection() {
    setSelectedDocuments([]);
  }

  async function handleSend(question) {
    // 1. Show the user's own message right away
    const userMessage = { role: "user", text: question };
    setMessages((previous) => [...previous, userMessage]);

    setIsLoading(true);

    try {
      // 2. Ask the backend (this hits /documents/ask), passing along
      // whichever documents (if any) the user selected
      const response = await askQuestion(question, selectedDocuments);

      const assistantMessage = {
        role: "assistant",
        text: response.answer,
        sources: response.sources,
      };

      setMessages((previous) => [...previous, assistantMessage]);
    } catch (error) {
      // 3. If anything goes wrong (server down, network error, etc.)
      // show a message in the chat instead of crashing the app.
      const errorMessage = {
        role: "assistant",
        text:
          "Sorry, something went wrong while getting your answer. Please check that the backend is running and try again.",
      };

      setMessages((previous) => [...previous, errorMessage]);
      console.error("Failed to get answer:", error);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="chat-page">
      {availableDocumentNames.length > 0 && (
        <div className="document-filter-bar">
          <div className="document-filter-header">
            <span className="document-filter-label">Search in:</span>

            {selectedDocuments.length > 0 && (
              <button
                className="document-filter-clear"
                onClick={clearDocumentSelection}
              >
                Clear selection
              </button>
            )}
          </div>

          <div className="document-filter-chips">
            {availableDocumentNames.map((name) => (
              <button
                key={name}
                className={`document-filter-chip ${
                  selectedDocuments.includes(name)
                    ? "document-filter-chip-active"
                    : ""
                }`}
                onClick={() => toggleDocumentSelection(name)}
              >
                {name}
              </button>
            ))}
          </div>

          <div className="document-filter-status">
            {selectedDocuments.length === 0
              ? "No documents selected — searching all uploaded documents."
              : `Searching only in ${selectedDocuments.length} selected document(s).`}
          </div>
        </div>
      )}

      <main className="chat-area">
        {messages.length === 0 && (
          <div className="empty-state">
            Ask a question about your uploaded documents to get started.
          </div>
        )}

        {messages.map((message, index) => (
          <ChatMessage
            key={index}
            role={message.role}
            text={message.text}
            sources={message.sources}
          />
        ))}

        {isLoading && (
          <div className="message-row assistant-row">
            <div className="message-bubble assistant-bubble loading-bubble">
              <span className="loading-dot"></span>
              <span className="loading-dot"></span>
              <span className="loading-dot"></span>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </main>

      <ChatInput onSend={handleSend} disabled={isLoading} />
    </div>
  );
}

export default ChatPage;
