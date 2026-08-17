// Renders ONE message bubble -- either from the user or the
// assistant. If it's an assistant message and it has sources, we
// show those underneath the answer, inside the SAME bubble, so it's
// visually unambiguous which answer each citation belongs to.

function ChatMessage({ role, text, sources }) {
  const isUser = role === "user";

  return (
    <div className={`message-row ${isUser ? "user-row" : "assistant-row"}`}>
      <div className={`message-bubble ${isUser ? "user-bubble" : "assistant-bubble"}`}>
        <p className="message-text">{text}</p>

        {sources && sources.length > 0 && (
          <div className="sources-box">
            <div className="sources-title">
              Sources ({sources.length})
            </div>

            <div className="sources-list">
              {sources.map((source, index) => (
                <div className="source-card" key={index}>
                  <span className="source-field source-document">
                    {source.document}
                  </span>
                  <span className="source-field">
                    Page {source.page}
                  </span>
                  <span className="source-field">
                    Chunk {source.chunk}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default ChatMessage;
