import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Options } from "./Options";
import "../shared/theme/theme.css";

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <Options />
  </StrictMode>
);
