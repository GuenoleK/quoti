import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";

export default defineConfig({
  plugins: [react(), wrapContentScript()],
  build: {
    emptyOutDir: true,
    rollupOptions: {
      input: {
        options: "options.html",
        popup: "popup.html",
        "content-script": "src/extension/content/content-script.ts",
        "service-worker": "src/extension/background/service-worker.ts"
      },
      output: {
        entryFileNames: "[name].js",
        chunkFileNames: "assets/[name].js",
        assetFileNames: "assets/[name][extname]"
      }
    }
  }
});

function wrapContentScript(): Plugin {
  return {
    name: "wrap-content-script",
    generateBundle(_options, bundle): void {
      const chunk = bundle["content-script.js"];

      if (chunk?.type === "chunk") {
        chunk.code = `{\n${chunk.code}\n}`;
      }
    }
  };
}
