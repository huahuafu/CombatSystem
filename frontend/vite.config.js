import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/app/",
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/combat": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: "../src/main/resources/static/app",
    emptyOutDir: true
  }
});
