// A small, generic confirm/cancel modal. Not tied to documents
// specifically, so it could be reused elsewhere later, but for now
// it exists to ask "replace this existing file?" before uploading.

function ConfirmDialog({ message, confirmLabel, cancelLabel, onConfirm, onCancel }) {
  return (
    <div className="confirm-overlay">
      <div className="confirm-dialog">
        <p className="confirm-message">{message}</p>

        <div className="confirm-actions">
          <button className="confirm-cancel-button" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button className="confirm-replace-button" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export default ConfirmDialog;
