export function normalizeCompanyCode(value?: string | null): string {
  return (value ?? "").trim().toUpperCase();
}

export function isCompanyMatch(
  employeeCompanyCode: string | null | undefined,
  selectedCompany: string,
): boolean {
  if (selectedCompany === "All Companies") {
    return true;
  }

  return (
    normalizeCompanyCode(employeeCompanyCode) ===
    normalizeCompanyCode(selectedCompany)
  );
}