import { useState, useRef, useEffect } from "react";
import ChatMessage from "../components/ChatMessage";
import ChatInput from "../components/ChatInput";
import { askQuestion } from "../api";

// This is the exact same chat logic that used to live directly in
// App.jsx -- moved here unchanged so it can be shown/hidden as its
// own "page", without touching how the chat itself behaves.

function ChatPage() {
  // Every message we show on screen: { role: "user" | "assistant", text, sources? }
  const [messages, setMessages] = useState([]);

  // True while we're waiting for the backend to respond
  const [isLoading, setIsLoading] = useState(false);

  // Used to auto-scroll to the latest message
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  async function handleSend(question) {
    // 1. Show the user's own message right away
    const userMessage = { role: "user", text: question };
    setMessages((previous) => [...previous, userMessage]);

    setIsLoading(true);

    try {
      // 2. Ask the backend (this hits /documents/ask)
      const response = await askQuestion(question);

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
