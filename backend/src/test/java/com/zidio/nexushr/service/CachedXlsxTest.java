package com.zidio.nexushr.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedXlsxTest {

    @Test
    void readsSalaryWorkbookWithoutLoadingEveryCell() throws Exception {
        Path workbook = Path.of("GGEA-CYOND-SALARIES-2026.xlsx");
        List<String> names = CachedXlsx.sheetNames(workbook);
        assertTrue(names.contains("CONSTRUCTIONS DEPARTMENT"));
        assertTrue(names.contains("Employs Salaris"));

        List<CachedXlsx.XlsxSheet> sheets = CachedXlsx.read(workbook, new CachedXlsx.SheetPlan() {
            @Override
            public Set<Integer> columnsAfterHeader(String sheetName, CachedXlsx.XlsxSheet headerRows) {
                if ("Employs Salaris".equalsIgnoreCase(sheetName)) {
                    return null;
                }
                if ("CONSTRUCTIONS DEPARTMENT".equals(sheetName)) {
                    return Set.of(25, 26, 28);
                }
                return Set.of();
            }

            @Override
            public Set<Integer> identityColumns(String sheetName, CachedXlsx.XlsxSheet headerRows) {
                if ("Employs Salaris".equalsIgnoreCase(sheetName)) {
                    return null;
                }
                if ("CONSTRUCTIONS DEPARTMENT".equals(sheetName)) {
                    return Set.of(25, 26);
                }
                return Set.of();
            }
        });

        CachedXlsx.XlsxSheet constructions = sheets.stream()
                .filter(sheet -> "CONSTRUCTIONS DEPARTMENT".equals(sheet.getName()))
                .findFirst()
                .orElseThrow();
        CachedXlsx.XlsxRow employee = constructions.getRow(13);
        assertEquals("351914", employee.get(25));
        assertEquals("SRINIVAS.S", employee.get(26));
        assertTrue(employee.get(28).startsWith("171:"));
        assertTrue(constructions.storedCellCount() < 5000, "stored cells: " + constructions.storedCellCount());

        CachedXlsx.XlsxSheet salaries = sheets.stream()
                .filter(sheet -> "Employs Salaris".equals(sheet.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Faheem.M.A", salaries.getRow(1).get(3));
    }
}
