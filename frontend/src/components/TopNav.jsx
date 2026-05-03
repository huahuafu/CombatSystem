import React from "react";
import { NavLink, useLocation } from "react-router-dom";

const items = [
  { to: "/app/commander-next", label: "态势与部署", match: (path) => path.includes("/commander-next") },
  { to: "/app/simulation-dashboard", label: "仿真运行", match: (path) => path.includes("/simulation-dashboard") },
  { to: "/app/run-center", label: "复盘回放", match: (path) => path.includes("/run-center") }
];

export default function TopNav() {
  const location = useLocation();

  return (
    <header className="app-topbar">
      <div className="app-brand">CombatSystem 指挥终端</div>
      <nav className="app-nav">
        {items.map((l) => {
          const active = l.match(location.pathname);
          return (
            <NavLink
              key={l.to}
              to={l.to}
              className={() => `app-nav-link ${active ? "active" : ""}`}
            >
              {l.label}
            </NavLink>
          );
        })}
      </nav>
      <div className="app-nav-right">OpenLayers · 推演链路</div>
    </header>
  );
}
