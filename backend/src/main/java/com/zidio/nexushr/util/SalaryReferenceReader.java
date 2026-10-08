package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;

public class SalaryReferenceReader {

    public static void main(String[] args) throws Exception {

        try (FileInputStream fis = new FileInputStream("GGEA-CYOND-SALARIES-2026.xlsx");
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("Employs Salaris");

            if (sheet == null) {
                throw new RuntimeException("Employs Salaris sheet not found");
            }

            System.out.println("===== EMPLOYS SALARIS =====");

            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {

                if (row == null) {
                    continue;
                }

                StringBuilder line = new StringBuilder();

                for (int c = 0; c < row.getLastCellNum(); c++) {

                    Cell cell = row.getCell(c);

                    if (cell == null) {
                        line.append("[").append(c).append("] EMPTY | ");
                    } else {
                        line.append("[")
                            .append(c)
                            .append("] ")
                            .append(formatter.formatCellValue(cell))
                            .append(" | ");
                    }
                }

                System.out.println("ROW " + (row.getRowNum() + 1) + ": " + line);
            }
        }
    }
}
