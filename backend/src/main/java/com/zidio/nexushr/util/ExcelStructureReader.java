package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;

public class ExcelStructureReader {

    public static void main(String[] args) throws Exception {

        String filePath = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            System.out.println("===== SHEETS =====");

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

                Sheet sheet = workbook.getSheetAt(i);

                System.out.println();
                System.out.println("SHEET " + (i + 1) + ": " + sheet.getSheetName());
                System.out.println("Rows: " + sheet.getPhysicalNumberOfRows());

                int maxRows = Math.min(sheet.getPhysicalNumberOfRows(), 8);

                for (int r = 0; r < maxRows; r++) {

                    Row row = sheet.getRow(r);

                    if (row == null) {
                        continue;
                    }

                    System.out.print("ROW " + (r + 1) + ": ");

                    for (int c = 0; c < row.getLastCellNum(); c++) {

                        Cell cell = row.getCell(c);

                        if (cell == null) {
                            System.out.print("[EMPTY] | ");
                        } else {
                            System.out.print(
                                "[" + c + "] " + cell.toString().trim() + " | "
                            );
                        }
                    }

                    System.out.println();
                }
            }
        }
    }
}
