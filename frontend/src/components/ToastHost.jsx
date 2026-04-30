import React from "react";

export default function ToastHost({ toasts }) {
  if (!toasts?.length) return null;
  return (
    <div className="toastWrap">
      {toasts.map((t) => (
        <div key={t.id} className={`toast ${t.level || "info"}`}>
          <div className="t">{t.title || "提示"}</div>
          <div className="m">{t.message}</div>
        </div>
      ))}
    </div>
  );
}
