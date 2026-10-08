package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.File;
import java.util.Iterator;

public class PayrollFormulaReader {

    public static void main(String[] args) throws Exception {

        String filePath = "GGEA-CYOND-SALARIES-2026.xlsx";

        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("ERROR: Excel file not found: " + file.getAbsolutePath());
            return;
        }

        try (Workbook workbook = WorkbookFactory.create(file)) {

            String[] keywords = {
                "basic",
                "hra",
                "conveyance",
                "medical",
                "other",
                "pf",
                "esi",
                "pt",
                "final pay",
                "final",
                "deduction",
                "salary",
                "gross",
                "ctc",
                "charge",
                "earned"
            };

            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {

                boolean printedSheet = false;

                for (Row row : sheet) {
                    for (Cell cell : row) {

                        String value = formatter.formatCellValue(cell);

                        if (value == null || value.trim().isEmpty()) {
                            continue;
                        }

                        String lower = value.toLowerCase();

                        boolean match = false;

                        for (String keyword : keywords) {
                            if (lower.contains(keyword)) {
                                match = true;
                                break;
                            }
                        }

                        if (!match) {
                            continue;
                        }

                        if (!printedSheet) {
                            System.out.println();
                            System.out.println("==================================================");
                            System.out.println("SHEET: " + sheet.getSheetName());
                            System.out.println("==================================================");
                            printedSheet = true;
                        }

                        String formula = "";

                        if (cell.getCellType() == CellType.FORMULA) {
                            formula = cell.getCellFormula();
                        }

                        System.out.println(
                            "CELL=" + cell.getAddress()
                            + " | VALUE=" + value
                            + " | FORMULA=" + formula
                        );
                    }
                }
            }

            System.out.println();
            System.out.println("Payroll formula scan completed.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
