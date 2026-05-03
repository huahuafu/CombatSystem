import React from "react";
import ReactDOM from "react-dom/client";
import { ConfigProvider, theme } from "antd";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "antd/dist/reset.css";
import "./styles/tailwind.css";
import "./styles/theme.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorBgBase: "#0a0e17",
          colorBgLayout: "#0a0e17",
          colorBgContainer: "#0f172a",
          colorBorder: "#334155",
          colorTextBase: "#e2e8f0",
          colorPrimary: "#2563eb",
          colorError: "#dc2626",
          borderRadius: 12
        }
      }}
    >
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ConfigProvider>
  </React.StrictMode>
);
