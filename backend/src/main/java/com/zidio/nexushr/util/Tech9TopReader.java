package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import java.io.FileInputStream;

public class Tech9TopReader {

    public static void main(String[] args) throws Exception {

        String file = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheet("TECH 9");

            if (sheet == null) {
                System.out.println("TECH 9 sheet not found");
                return;
            }

            DataFormatter formatter = new DataFormatter();

            for (int r = 0; r <= 12; r++) {

                System.out.println();
                System.out.println("===== ROW " + (r + 1) + " =====");

                Row row = sheet.getRow(r);

                if (row == null) {
                    continue;
                }

                for (int c = 0; c <= 57; c++) {

                    Cell cell = row.getCell(c);

                    if (cell == null) {
                        continue;
                    }

                    String value = formatter.formatCellValue(cell).trim();

                    if (!value.isEmpty()) {
                        System.out.println(
                            "COL " + CellReference.convertNumToColString(c)
                            + " = [" + value + "]"
                        );
                    }
                }
            }
        }
    }
}