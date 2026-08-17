// Left-hand navigation. Purely presentational -- it just tells App.jsx
// which page was clicked, via onNavigate. App.jsx decides what to
// actually render.

const NAV_ITEMS = [
  { key: "chat", label: "Chat", icon: "💬" },
  { key: "documents", label: "Documents", icon: "📄" },
];

function Sidebar({ activePage, onNavigate }) {
  return (
    <nav className="sidebar">
      <div className="sidebar-brand">SmartDoc</div>

      <div className="sidebar-links">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.key}
            className={`sidebar-link ${
              activePage === item.key ? "sidebar-link-active" : ""
            }`}
            onClick={() => onNavigate(item.key)}
          >
            <span className="sidebar-icon">{item.icon}</span>
            <span>{item.label}</span>
          </button>
        ))}
      </div>
    </nav>
  );
}

export default Sidebar;
