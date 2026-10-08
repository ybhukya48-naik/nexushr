package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;

public class WorkingDaysScanner {

    public static void main(String[] args) throws Exception {

        String file = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : wb) {

                System.out.println();
                System.out.println("===== SHEET: " + sheet.getSheetName() + " =====");

                for (Row row : sheet) {

                    for (Cell cell : row) {

                        String value = formatter.formatCellValue(cell).trim();

                        if (value.equalsIgnoreCase("Working Days")
                                || value.equalsIgnoreCase("Working Day")
                                || value.toLowerCase().contains("working days")) {

                            int rowNum = cell.getRowIndex();
                            int colNum = cell.getColumnIndex();

                            System.out.println(
                                "FOUND: "
                                + cell.getAddress()
                                + " = [" + value + "]"
                            );

                            for (int c = Math.max(0, colNum - 2);
                                 c <= Math.min(row.getLastCellNum() - 1, colNum + 3);
                                 c++) {

                                Cell nearby = row.getCell(c);

                                if (nearby != null) {
                                    System.out.println(
                                        "  "
                                        + nearby.getAddress()
                                        + " = ["
                                        + formatter.formatCellValue(nearby)
                                        + "]"
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
