import { useState } from "react";

// The box at the bottom of the screen. It only knows how to collect
// text and call onSend -- it doesn't know anything about the API
// or the message list. That's App.jsx's job.

function ChatInput({ onSend, disabled }) {
  const [text, setText] = useState("");

  function handleSend() {
    const trimmed = text.trim();

    if (trimmed === "") {
      return;
    }

    onSend(trimmed);
    setText("");
  }

  function handleKeyDown(event) {
    // Enter sends the message, Shift+Enter still lets you add a
    // new line -- same behavior most chat apps use.
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  }

  return (
    <div className="chat-input-bar">
      <textarea
        className="chat-input"
        placeholder="Ask a question about your documents..."
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        rows={1}
      />
      <button
        className="send-button"
        onClick={handleSend}
        disabled={disabled}
      >
        Send
      </button>
    </div>
  );
}

export default ChatInput;
