import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Popup } from "./popup/Popup";
import "./shared/theme/theme.css";

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <Popup />
  </StrictMode>
);
