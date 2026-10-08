package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;

public class AttendanceEmployeeScanner {

    public static void main(String[] args) throws Exception {

        String file = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            DataFormatter f = new DataFormatter();

            for (Sheet sheet : wb) {

                Row row5 = sheet.getRow(4);

                if (row5 == null) {
                    continue;
                }

                Cell workingDaysCell = row5.getCell(26); // AA
                String workingDays = workingDaysCell == null
                        ? ""
                        : f.formatCellValue(workingDaysCell).trim();

                if (workingDays.isEmpty()) {
                    continue;
                }

                System.out.println();
                System.out.println("===== " + sheet.getSheetName()
                        + " | WORKING DAYS = " + workingDays + " =====");

                for (int r = 10; r < Math.min(sheet.getLastRowNum() + 1, 20); r++) {

                    Row row = sheet.getRow(r);

                    if (row == null) {
                        continue;
                    }

                    Cell code = row.getCell(1);   // B
                    Cell name = row.getCell(7);   // H

                    if (code != null || name != null) {

                        System.out.println(
                            "CODE=[" + (code == null ? "" : f.formatCellValue(code))
                            + "] NAME=["
                            + (name == null ? "" : f.formatCellValue(name))
                            + "]"
                        );
                    }
                }
            }
        }
    }
}
