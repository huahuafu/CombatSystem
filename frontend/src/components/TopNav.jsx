import React from "react";
import { NavLink } from "react-router-dom";

const links = [
  { to: "/app/commander-next", label: "重构工作台" },
  { to: "/app/simulation-dashboard", label: "仿真看板" },
  { to: "/app/run-center", label: "运行回放中心" },
  { to: "/app/campaign-center", label: "战役中心" }
];

export default function TopNav() {
  return (
    <div className="topbar">
      <div className="brand">CombatSystem · Frontend 工程化</div>
      <div className="nav">
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} className={({ isActive }) => (isActive ? "active" : "")}>
            {l.label}
          </NavLink>
        ))}
      </div>
      <div className="navRight">React + Vite</div>
    </div>
  );
}
