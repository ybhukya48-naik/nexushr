package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.File;

public class PayrollSalaryRowsReader {

    public static void main(String[] args) throws Exception {

        String filePath = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (Workbook workbook = WorkbookFactory.create(new File(filePath))) {

            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {

                for (Row row : sheet) {

                    boolean salaryHeader = false;

                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);

                        if (value == null) {
                            continue;
                        }

                        String v = value.trim().toLowerCase();

                        if (v.equals("ctc")
                                || v.equals("gross salary")
                                || v.equals("gross salay")) {
                            salaryHeader = true;
                            break;
                        }
                    }

                    if (!salaryHeader) {
                        continue;
                    }

                    System.out.println();
                    System.out.println("==================================================");
                    System.out.println("SHEET: " + sheet.getSheetName());
                    System.out.println("HEADER ROW: " + (row.getRowNum() + 1));
                    System.out.println("==================================================");

                    printRow(row, formatter);

                    int start = row.getRowNum() + 1;
                    int end = Math.min(sheet.getLastRowNum(), start + 15);

                    for (int r = start; r <= end; r++) {

                        Row dataRow = sheet.getRow(r);

                        if (dataRow == null) {
                            continue;
                        }

                        boolean hasData = false;

                        for (Cell cell : dataRow) {
                            String value = formatter.formatCellValue(cell);

                            if (value != null && !value.trim().isEmpty()) {
                                hasData = true;
                                break;
                            }
                        }

                        if (hasData) {
                            printRow(dataRow, formatter);
                        }
                    }
                }
            }
        }
    }

    private static void printRow(Row row, DataFormatter formatter) {

        System.out.println();
        System.out.println("ROW " + (row.getRowNum() + 1));

        for (Cell cell : row) {

            String value = formatter.formatCellValue(cell);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String formula = "";

            if (cell.getCellType() == CellType.FORMULA) {
                formula = cell.getCellFormula();
            }

            System.out.println(
                cell.getAddress()
                + " | VALUE=" + value
                + " | TYPE=" + cell.getCellType()
                + " | FORMULA=" + formula
            );
        }
    }
}