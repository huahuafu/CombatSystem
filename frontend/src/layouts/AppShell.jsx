import React from "react";
import TopNav from "../components/TopNav";

export default function AppShell({ children }) {
  return (
    <div className="app-shell">
      <TopNav />
      <main className="app-content">{children}</main>
    </div>
  );
}
