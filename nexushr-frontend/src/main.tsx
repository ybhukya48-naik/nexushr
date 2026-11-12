import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import "./ux-layout.css";
import "./company-theme.css";
import App from "./App.tsx";

(window as Window & { __nexushr_booted?: boolean }).__nexushr_booted = true;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
