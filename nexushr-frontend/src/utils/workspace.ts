export type WorkspaceCompany = "CYOND" | "GORLE";

const KEY = "nexushr_company";

export function getWorkspaceCompany(): WorkspaceCompany | null {
  const value = localStorage.getItem(KEY);
  if (value === "CYOND" || value === "GORLE") {
    return value;
  }
  return null;
}

export function setWorkspaceCompany(company: WorkspaceCompany) {
  localStorage.setItem(KEY, company);
  window.dispatchEvent(new Event("nexushr-company"));
}

export function clearWorkspaceCompany() {
  localStorage.removeItem(KEY);
}

export function asWorkspaceCompany(value?: string | null): WorkspaceCompany {
  const code = (value ?? "").trim().toUpperCase();
  if (code.includes("GORLE") || code.includes("GGEA") || code.includes("GORLE GROUP")) {
    return "GORLE";
  }
  return "CYOND";
}

export function workspaceName(company: WorkspaceCompany) {
  return company === "GORLE" ? "Gorle Group" : "CYOND";
}

export function workspaceTagline(company: WorkspaceCompany) {
  return company === "GORLE"
    ? "Structural Engineering"
    : "Waterproofing Diagnosis & Repair Experts";
}

export function workspaceLogo(company: WorkspaceCompany) {
  return company === "GORLE"
    ? "/images/gorle-group-logo.jpeg"
    : "/images/cyond-logo.jpeg";
}
