package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import java.io.FileInputStream;

public class PayrollSummaryScanner {

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

                Cell workingDaysCell = row5.getCell(26); // AA5

                if (workingDaysCell == null) {
                    continue;
                }

                String workingDays = f.formatCellValue(workingDaysCell).trim();

                if (workingDays.isEmpty()) {
                    continue;
                }

                System.out.println();
                System.out.println("==================================================");
                System.out.println("SHEET       : " + sheet.getSheetName());
                System.out.println("WORKING DAYS: " + workingDays);
                System.out.println("==================================================");

                // Payroll summary header is normally row 10
                Row header = sheet.getRow(9);

                if (header != null) {
                    System.out.println("SUMMARY HEADER:");

                    for (int c = 25; c <= 52; c++) {

                        Cell cell = header.getCell(c);

                        if (cell != null) {
                            String value = f.formatCellValue(cell).trim();

                            if (!value.isEmpty()) {
                                System.out.println(
                                    cell.getAddress()
                                    + " = [" + value + "]"
                                );
                            }
                        }
                    }
                }

                System.out.println();
                System.out.println("SUMMARY DATA:");

                // First 10 payroll summary rows
                for (int r = 10; r < Math.min(sheet.getLastRowNum() + 1, 20); r++) {

                    Row row = sheet.getRow(r);

                    if (row == null) {
                        continue;
                    }

                    String code = getValue(row, 25, f);       // Z
                    String name = getValue(row, 26, f);       // AA
                    String present = getValue(row, 27, f);    // AB
                    String payableHours = getValue(row, 33, f); // AH
                    String cl = getValue(row, 37, f);         // AL
                    String payableDays = getValue(row, 38, f); // AM
                    String chargePerDay = getValue(row, 39, f); // AN
                    String ctc = getValue(row, 40, f);        // AO
                    String gross = getValue(row, 41, f);      // AP
                    String earnedGross = getValue(row, 42, f); // AQ
                    String finalPay = getValue(row, 52, f);   // BA

                    System.out.println(
                        "ROW " + (r + 1)
                        + " | CODE=[" + code + "]"
                        + " | NAME=[" + name + "]"
                        + " | PRESENT=[" + present + "]"
                        + " | PAYABLE_HOURS=[" + payableHours + "]"
                        + " | CL=[" + cl + "]"
                        + " | PAYABLE_DAYS=[" + payableDays + "]"
                        + " | CHARGE_DAY=[" + chargePerDay + "]"
                        + " | CTC=[" + ctc + "]"
                        + " | GROSS=[" + gross + "]"
                        + " | EARNED_GROSS=[" + earnedGross + "]"
                        + " | FINAL_PAY=[" + finalPay + "]"
                    );
                }
            }
        }
    }

    private static String getValue(Row row, int column, DataFormatter f) {

        Cell cell = row.getCell(column);

        if (cell == null) {
            return "";
        }

        return f.formatCellValue(cell).trim();
    }
}
