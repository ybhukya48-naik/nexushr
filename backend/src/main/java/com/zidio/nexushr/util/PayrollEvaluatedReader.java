package com.zidio.nexushr.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.openxml4j.opc.OPCPackage;

import java.io.File;

public class PayrollEvaluatedReader {

    private static String value(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";

        CellType type = cell.getCellType();

        if (type == CellType.FORMULA) {
            CellValue v = evaluator.evaluate(cell);
            if (v == null) return "";

            return switch (v.getCellType()) {
                case STRING -> v.getStringValue();
                case NUMERIC -> Double.toString(v.getNumberValue());
                case BOOLEAN -> Boolean.toString(v.getBooleanValue());
                case ERROR -> "ERROR:" + v.getErrorValue();
                default -> "";
            };
        }

        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    public static void main(String[] args) throws Exception {

        File file = new File("GGEA-CYOND-SALARIES-2026.xlsx");

        try (Workbook wb = WorkbookFactory.create(file)) {

            FormulaEvaluator evaluator =
                    wb.getCreationHelper().createFormulaEvaluator();

            String[] sheets = {
                    "TECH 9",
                    "CYOND NDT",
                    "G GROUP STRUCTURAL DEPARTMENT",
                    "GGC AND AP OFC"
            };

            for (String sheetName : sheets) {

                Sheet sheet = wb.getSheet(sheetName);

                if (sheet == null) {
                    System.out.println("NOT FOUND: " + sheetName);
                    continue;
                }

                System.out.println();
                System.out.println("==================================================");
                System.out.println("SHEET: " + sheetName);
                System.out.println("==================================================");

                Row header = sheet.getRow(9);

                if (header == null) {
                    System.out.println("No row 10 header");
                    continue;
                }

                for (int c = 25; c <= 52; c++) {

                    Cell cell = header.getCell(c);

                    if (cell == null) continue;

                    String h = value(cell, evaluator);

                    if (!h.isBlank()) {
                        System.out.println(
                                CellReference.convertNumToColString(c)
                                        + "10 = [" + h + "]"
                        );
                    }
                }

                System.out.println();
                System.out.println("FIRST 10 EMPLOYEE ROWS:");

                for (int r = 10; r < Math.min(sheet.getLastRowNum() + 1, 20); r++) {

                    Row row = sheet.getRow(r);

                    if (row == null) continue;

                    String code = value(row.getCell(25), evaluator);
                    String name = value(row.getCell(26), evaluator);

                    if (code.isBlank() && name.isBlank()) {
                        continue;
                    }

                    System.out.println(
                            "ROW " + (r + 1)
                                    + " | CODE=[" + code + "]"
                                    + " | NAME=[" + name + "]"
                    );

                    String[] cols = {
                            "AB", "AC", "AD", "AE", "AF", "AG", "AH",
                            "AI", "AJ", "AK", "AL", "AM", "AN",
                            "AO", "AP", "AQ", "AR", "AS", "AT", "AU",
                            "AV", "AW", "AX", "AY", "AZ", "BA"
                    };

                    for (String col : cols) {

                        int c = CellReference.convertColStringToIndex(col);

                        Cell cell = row.getCell(c);

                        String formula = "";
                        String evaluated = value(cell, evaluator);

                        if (cell != null && cell.getCellType() == CellType.FORMULA) {
                            formula = cell.getCellFormula();
                        }

                        System.out.println(
                                "   " + col
                                        + " = value[" + evaluated + "]"
                                        + (formula.isBlank()
                                        ? ""
                                        : " formula[" + formula + "]")
                        );
                    }

                    System.out.println();
                }
            }
        }
    }
}
