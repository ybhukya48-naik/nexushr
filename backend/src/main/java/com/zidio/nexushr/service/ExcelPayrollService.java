
package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import org.apache.poi.ss.usermodel.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ExcelPayrollService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal NINE = BigDecimal.valueOf(9);

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    public ExcelPayrollService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository) {

        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public List<PayrollRecord> importPayroll(
            MultipartFile file,
            String payMonth) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is empty.");
        }

        if (payMonth == null || payMonth.isBlank()) {
            throw new IllegalArgumentException("payMonth is required.");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();

            SalarySource salarySource =
                    readSalarySource(workbook, evaluator);

            if (salarySource.rows().isEmpty()) {
                throw new IllegalStateException(
                        "No employee salary data found in 'Employs Salaris' sheet."
                );
            }

            List<PayrollRecord> created = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (Sheet sheet : workbook) {

                if (isSalarySheet(sheet)) {
                    continue;
                }

                PayrollSheet payrollSheet =
                        detectPayrollSheet(sheet);

                if (payrollSheet == null) {
                    continue;
                }

                for (int rowIndex = payrollSheet.dataStartRow();
                     rowIndex <= sheet.getLastRowNum();
                     rowIndex++) {

                    Row row = sheet.getRow(rowIndex);

                    if (row == null) {
                        continue;
                    }

                    String employeeCode =
                            readString(row, payrollSheet.employeeCodeColumn(), evaluator);

                    String employeeName =
                            readString(row, payrollSheet.employeeNameColumn(), evaluator);

                    if (isBlank(employeeCode) || isBlank(employeeName)) {
                        continue;
                    }

                    if (isInvalidEmployeeRow(employeeCode, employeeName)) {
                        continue;
                    }

                    try {

                        SalaryRow salary =
                                salarySource.find(employeeCode, employeeName);

                        boolean hasSheetCtc =
                                payrollSheet.ctcColumn() != null
                                        && value(
                                                row,
                                                payrollSheet.ctcColumn(),
                                                evaluator
                                        ).compareTo(ZERO) > 0;

                        boolean hasSheetGross =
                                payrollSheet.grossSalaryColumn() != null
                                        && value(
                                                row,
                                                payrollSheet.grossSalaryColumn(),
                                                evaluator
                                        ).compareTo(ZERO) > 0;

                        boolean hasSheetSalary =
                                hasSheetCtc || hasSheetGross;

                        if (salary == null && !hasSheetSalary) {
                            throw new IllegalStateException(
                                    "Salary source match not found and payroll sheet has no CTC/Gross for "
                                            + employeeCode + " / "
                                            + employeeName
                            );
                        }

                        Employee employee =
                                employeeRepository
                                        .findByEmployeeCodeIgnoreCase(employeeCode)
                                        .orElseThrow(() ->
                                                new IllegalStateException(
                                                        "Employee not found in DB: "
                                                                + employeeCode
                                                )
                                        );

                        Company company = employee.getCompany();

                        if (company == null) {
                            throw new IllegalStateException(
                                    "Employee has no company: "
                                            + employeeCode
                                            + " / "
                                            + employeeName
                            );
                        }

                        validateEmployee(employee, company, employeeName);

                        if (payrollRepository.existsByEmployee_IdAndPayMonth(
                                employee.getId(),
                                payMonth)) {

                            throw new IllegalStateException(
                                    "Payroll already exists for employee "
                                            + employeeCode
                                            + " and month "
                                            + payMonth
                            );
                        }

                        PayrollRecord payroll =
                                buildPayrollRecord(
                                        employee,
                                        payMonth,
                                        row,
                                        payrollSheet,
                                        salary,
                                        evaluator
                                );

                        created.add(
                                payrollRepository.save(payroll)
                        );

                    } catch (Exception ex) {

                        errors.add(
                                "Sheet [" + sheet.getSheetName()
                                        + "], row [" + (rowIndex + 1)
                                        + "], employee [" + employeeCode
                                        + " / " + employeeName
                                        + "]: " + ex.getMessage()
                        );
                    }
                }
            }

            if (created.isEmpty() && !errors.isEmpty()) {
                throw new IllegalStateException(
                        "Excel payroll import failed:\n"
                                + String.join("\n", errors)
                );
            }

            return created;

        } catch (IllegalStateException ex) {
            throw ex;

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to read payroll Excel: " + ex.getMessage(),
                    ex
            );
        }
    }

    private PayrollRecord buildPayrollRecord(
            Employee employee,
            String payMonth,
            Row row,
            PayrollSheet sheet,
            SalaryRow salary,
            FormulaEvaluator evaluator) {

        PayrollRecord payroll = new PayrollRecord();

        payroll.setEmployee(employee);
        payroll.setPayMonth(payMonth);
        payroll.setStatus(PayrollStatus.GENERATED);

        /*
         * ============================================================
         * SALARY SOURCE
         * ============================================================
         */

        BigDecimal ctc = salary.ctc();

        BigDecimal grossSalary = salary.gross();

        if (grossSalary.compareTo(ZERO) <= 0) {
            grossSalary = ctc
                    .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        }

        payroll.setCtc(ctc);
        payroll.setGrossSalary(grossSalary);

        /*
         * ============================================================
         * WORKBOOK PAYROLL VALUES
         * ============================================================
         */

        BigDecimal presentDays =
                value(row, sheet.presentDaysColumn(), evaluator);

        BigDecimal actualWorkingHours =
                value(row, sheet.actualWorkingHoursColumn(), evaluator);

        BigDecimal payableHours =
                value(row, sheet.payableHoursColumn(), evaluator);

        BigDecimal lateCut =
                value(row, sheet.lateCutColumn(), evaluator);

        BigDecimal casualLeave =
                value(row, sheet.casualLeaveColumn(), evaluator);

        BigDecimal payableDays =
                value(row, sheet.payableDaysColumn(), evaluator);

        BigDecimal chargePerDay =
                value(row, sheet.chargePerDayColumn(), evaluator);

        BigDecimal earnedGross =
                value(row, sheet.earnedGrossColumn(), evaluator);

        BigDecimal basic =
                value(row, sheet.basicColumn(), evaluator);

        BigDecimal earnedBasic =
                value(row, sheet.earnedBasicColumn(), evaluator);

        BigDecimal hra =
                value(row, sheet.hraColumn(), evaluator);

        BigDecimal conveyance =
                value(row, sheet.conveyanceColumn(), evaluator);

        BigDecimal medical =
                value(row, sheet.medicalColumn(), evaluator);

        BigDecimal others =
                value(row, sheet.othersColumn(), evaluator);

        BigDecimal pf =
                value(row, sheet.pfColumn(), evaluator);

        BigDecimal esi =
                value(row, sheet.esiColumn(), evaluator);

        BigDecimal pt =
                value(row, sheet.ptColumn(), evaluator);

        BigDecimal finalPay =
                value(row, sheet.finalPayColumn(), evaluator);

        /*
         * ============================================================
         * RECORD
         * ============================================================
         */

        payroll.setWorkingDays(
                sheet.workingDays()
        );

        payroll.setRequiredHours(NINE);

        payroll.setActualHours(
                actualWorkingHours
        );

        payroll.setCasualLeave(
                casualLeave
        );

        payroll.setOtherLeave(
                ZERO
        );

        payroll.setChargePerDay(
                chargePerDay
        );

        payroll.setEarnedGross(
                earnedGross
        );

        payroll.setBasicSalary(
                basic.compareTo(ZERO) > 0
                        ? basic
                        : grossSalary.multiply(
                                BigDecimal.valueOf(0.61)
                        )
        );

        payroll.setHra(hra);
        payroll.setConveyance(conveyance);
        payroll.setMedical(medical);
        payroll.setOthers(others);

        payroll.setBonus(ZERO);
        payroll.setOvertime(ZERO);

        payroll.setPf(pf);
        payroll.setEsi(esi);
        payroll.setPt(pt);

        /*
         * TECH 9 has an explicit Final Pay column.
         *
         * Other workbook sheets do not always have a final-pay
         * column. In those sheets we do NOT invent a formula.
         */
        if (sheet.finalPayColumn() >= 0) {
            payroll.setNetSalary(finalPay);
        } else {
            /*
             * This sheet does not contain Final Pay.
             * Do not invent a net-pay formula.
             */
            payroll.setNetSalary(ZERO);
        }

        payroll.setTaxAmount(ZERO);

        /*
         * Workbook's late/leave deductions are already reflected
         * in payable days / earned gross. Do not deduct them again.
         */
        payroll.setLeaveDeduction(ZERO);

        /*
         * Late/leave adjustments are already represented by
         * the workbook payable-hours / payable-days / earned-gross
         * calculations. Do not deduct them again.
         */
        payroll.setShortHoursDeduction(ZERO);

        payroll.setOtherDeductions(ZERO);

        payroll.setDeductions(
                pf
                        .add(esi)
                        .add(pt)
                        .add(payroll.getLeaveDeduction())
                        .add(payroll.getOtherDeductions())
        );


        /*
         * Attendance minute fields.
         *
         * Excel stores time as fraction of a day.
         */
        int workedMinutes =
                hoursToMinutes(actualWorkingHours);

        int expectedMinutes =
                sheet.workingDays() * 9 * 60;

        payroll.setExpectedWorkMinutes(expectedMinutes);
        payroll.setWorkedMinutes(workedMinutes);

        payroll.setShortfallMinutes(
                Math.max(0, expectedMinutes - workedMinutes)
        );

        payroll.setOvertimeMinutes(0);

        return payroll;
    }


    private SalarySource readSalarySource(
            Workbook workbook,
            FormulaEvaluator evaluator) {

        Sheet sheet = workbook.getSheet("Employs Salaris");

        if (sheet == null) {
            throw new IllegalStateException(
                    "Sheet 'Employs Salaris' not found."
            );
        }

        List<SalaryRow> rows = new ArrayList<>();

        String currentCompany = "GGEA";

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {

            Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }

            String id =
                    readString(row, 1, evaluator);

            String name =
                    readString(row, 3, evaluator);

            String ctcText =
                    readString(row, 4, evaluator);

            String grossText =
                    readString(row, 5, evaluator);

            if (id.equalsIgnoreCase("ID No")
                    || name.equalsIgnoreCase("Name")) {
                continue;
            }

            /*
             * Workbook contains GGEA section first and CYOND
             * section later. Detect CYOND from the section marker.
             */
            String completeRow =
                    rowToText(row, evaluator).toUpperCase(Locale.ROOT);

            if (completeRow.contains("CYOND")) {
                currentCompany = "CYOND";
            }

            if (isBlank(id) || isBlank(name)) {
                continue;
            }

            if (!isNumericEmployeeCode(id)) {
                continue;
            }

            BigDecimal ctc =
                    parseNumber(ctcText);

            BigDecimal gross =
                    parseNumber(grossText);

            if (ctc.compareTo(ZERO) <= 0) {
                continue;
            }

            rows.add(
                    new SalaryRow(
                            currentCompany,
                            normalizeCode(id),
                            normalizeName(name),
                            ctc,
                            gross
                    )
            );
        }

        return new SalarySource(rows);
    }

    private void validateEmployee(
            Employee employee,
            Company company,
            String excelName) {

        if (employee.getCompany() == null) {
            throw new IllegalStateException(
                    "Employee has no company."
            );
        }

        if (!Objects.equals(
                employee.getCompany().getId(),
                company.getId())) {

            throw new IllegalStateException(
                    "Company mismatch. Excel company="
                            + company.getCode()
                            + ", DB company="
                            + employee.getCompany().getCode()
            );
        }

        if (!normalizeName(employee.getFullName())
                .equals(normalizeName(excelName))) {

            throw new IllegalStateException(
                    "Employee name mismatch. Excel="
                            + excelName
                            + ", DB="
                            + employee.getFullName()
            );
        }
    }

    private PayrollSheet detectPayrollSheet(Sheet sheet) {

        /*
         * CYOND NDT is a special workbook layout:
         * row 10 contains data directly and there is no header row.
         */
        if ("CYOND NDT".equalsIgnoreCase(sheet.getSheetName())) {
            return detectCyondNdtSheet(sheet);
        }

        int headerRowIndex = -1;

        Map<String, Integer> headers =
                new HashMap<>();

        for (int i = 0;
             i <= Math.min(sheet.getLastRowNum(), 20);
             i++) {

            Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }

            Map<String, Integer> current =
                    readHeaders(row);

            if (hasAny(
                    current,
                    "EMPLOYEE CODE",
                    "EMPLOYEE NAME",
                    "TOTAL PRESENT DAYS",
                    "CTC",
                    "GROSS SALARY"
            )) {

                headerRowIndex = i;
                headers = current;
                break;
            }
        }

        if (headerRowIndex < 0) {
            return null;
        }

        Integer employeeCode =
                first(headers,
                        "EMPLOYEE CODE",
                        "EMPLOYEE CODE ");

        Integer employeeName =
                first(headers,
                        "EMPLOYEE NAME",
                        "NAME");

        Integer presentDays =
                first(headers,
                        "TOTAL PRESENT DAYS");

        Integer actualWorkingHours =
                first(headers,
                        "ACTUAL WORKING HOURS");

        Integer payableHours =
                first(headers,
                        "PAYABLE HOURS");

        if (payableHours == null) {
            payableHours =
                    first(headers,
                            "NET PAYABLE HOURS");
        }

        Integer lateCut =
                first(headers,
                        "LATE COME CUTT (IN DAYS)",
                        "LATE COME CUTT (IN HOURS)");

        Integer casualLeave =
                first(headers,
                        "CL");

        Integer payableDays =
                first(headers,
                        "NET PAYABLE DAYS");

        Integer chargePerDay =
                first(headers,
                        "CHARGE PER DAY");

        Integer ctcColumn =
                first(headers,
                        "CTC");

        Integer grossSalaryColumn =
                first(headers,
                        "GROSS SALARY",
                        "GROSS");

        Integer earnedGross =
                first(headers,
                        "EARNED GROSS");

        Integer basic =
                first(headers,
                        "BASIC");

        Integer earnedBasic =
                first(headers,
                        "EARNED BASIC");

        Integer hra =
                first(headers,
                        "HRA");

        Integer conveyance =
                first(headers,
                        "CONVEYANCE");

        Integer medical =
                first(headers,
                        "MEDICAL");

        Integer others =
                first(headers,
                        "OTHERS");

        Integer pf =
                first(headers,
                        "PF");

        Integer esi =
                first(headers,
                        "ESI");

        Integer pt =
                first(headers,
                        "PT");

        Integer finalPay =
                first(headers,
                        "FINAL PAY");

        /*
         * We only accept a payroll sheet when the essential
         * employee + salary columns exist.
         */
        if (employeeCode == null
                || employeeName == null
                || payableDays == null
                || chargePerDay == null
                || earnedGross == null) {

            return null;
        }

        int workingDays =
                detectWorkingDays(sheet, headerRowIndex);

        return new PayrollSheet(
                sheet.getSheetName(),
                headerRowIndex + 1,
                employeeCode,
                employeeName,
                presentDays,
                actualWorkingHours,
                payableHours,
                lateCut,
                casualLeave,
                payableDays,
                chargePerDay,
                ctcColumn,
                grossSalaryColumn,
                earnedGross,
                basic,
                earnedBasic,
                hra,
                conveyance,
                medical,
                others,
                pf,
                esi,
                pt,
                finalPay,
                workingDays
        );
    }

    private PayrollSheet detectCyondNdtSheet(Sheet sheet) {

        /*
         * CYOND NDT Z:BA layout:
         *
         * Z  Employee Code
         * AA Employee Name
         * AB Total Present Days
         * AC Actual Working Hours
         * AI Payable Hours
         * AK Late Come Cutt
         * AN CL
         * AO Net Payable Days
         * AP Charge Per Day
         * AR Earned Gross
         * AT Basic
         * AU Earned Basic
         * AV HRA
         * AW Conveyance
         * AX Medical
         * AY Others
         * AZ PF
         * BA ESI
         */

        int workingDays = detectWorkingDays(sheet, 9);

        return new PayrollSheet(
                sheet.getSheetName(),
                9,

                25,     // Z
                26,     // AA
                27,     // AB
                28,     // AC
                34,     // AI
                36,     // AK
                39,     // AN
                40,     // AO
                41,     // AP
                null,   // CTC
                null,   // Gross Salary
                44,     // AS Earned Gross
                45,     // AT Basic
                46,     // AU Earned Basic
                47,     // AV HRA
                48,     // AW Conveyance
                49,     // AX Medical
                50,     // AY Others
                51,     // AZ PF
                52,     // BA ESI
                null,   // PT
                null,   // Final Pay
                workingDays
        );
    }
    private int detectWorkingDays(
            Sheet sheet,
            int headerRow) {

        /*
         * Read working days from the workbook.
         * Never invent a default such as 30.
         *
         * Main payroll sheets use AA5, but we inspect the
         * nearby monthly-summary columns as well.
         */
        int[] preferredColumns = {
                26, // AA
                25, // Z
                27, // AB
                24  // Y
        };

        for (int rowIndex = 0; rowIndex < headerRow; rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            for (int column : preferredColumns) {

                Cell cell = row.getCell(column);

                if (cell == null) {
                    continue;
                }

                BigDecimal value =
                        value(row, column, null);

                if (value.compareTo(BigDecimal.ZERO) > 0
                        && value.compareTo(BigDecimal.valueOf(31)) <= 0) {

                    int days = value.intValue();

                    if (days >= 1 && days <= 31) {
                        return days;
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Working days not found in Excel sheet '"
                        + sheet.getSheetName()
                        + "'. Payroll import stopped because "
                        + "working days cannot be invented."
        );
    }

    private Map<String, Integer> readHeaders(Row row) {

        Map<String, Integer> result =
                new HashMap<>();

        for (int c = 0; c < row.getLastCellNum(); c++) {

            Cell cell = row.getCell(c);

            if (cell == null) {
                continue;
            }

            String value =
                    cell.toString()
                            .trim()
                            .replaceAll("\\s+", " ")
                            .toUpperCase(Locale.ROOT);

            if (!value.isBlank()) {
                result.put(value, c);
            }
        }

        return result;
    }

    private boolean hasAny(
            Map<String, Integer> headers,
            String... names) {

        for (String name : names) {
            if (headers.containsKey(name)) {
                return true;
            }
        }

        return false;
    }

    private Integer first(
            Map<String, Integer> headers,
            String... names) {

        for (String name : names) {
            Integer value = headers.get(name);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private BigDecimal value(
            Row row,
            Integer column,
            FormulaEvaluator evaluator) {

        if (column == null || column < 0) {
            return ZERO;
        }

        Cell cell = row.getCell(column);

        if (cell == null) {
            return ZERO;
        }

        try {

            if (cell.getCellType() == CellType.FORMULA
                    && evaluator != null) {

                CellValue evaluated =
                        evaluator.evaluate(cell);

                if (evaluated == null) {
                    return ZERO;
                }

                return switch (evaluated.getCellType()) {

                    case NUMERIC ->
                            BigDecimal.valueOf(
                                    evaluated.getNumberValue()
                            );

                    case STRING ->
                            parseNumber(
                                    evaluated.getStringValue()
                            );

                    default -> ZERO;
                };
            }

            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(
                        cell.getNumericCellValue()
                );
            }

            if (cell.getCellType() == CellType.STRING) {
                return parseNumber(
                        cell.getStringCellValue()
                );
            }

        } catch (Exception ignored) {
            return ZERO;
        }

        return ZERO;
    }

    private String readString(
            Row row,
            Integer column,
            FormulaEvaluator evaluator) {

        if (column == null || column < 0) {
            return "";
        }

        Cell cell = row.getCell(column);

        if (cell == null) {
            return "";
        }

        try {

            if (cell.getCellType() == CellType.FORMULA
                    && evaluator != null) {

                CellValue evaluated =
                        evaluator.evaluate(cell);

                if (evaluated == null) {
                    return "";
                }

                if (evaluated.getCellType()
                        == CellType.STRING) {

                    return evaluated
                            .getStringValue()
                            .trim();
                }

                if (evaluated.getCellType()
                        == CellType.NUMERIC) {

                    return formatNumericCode(
                            evaluated.getNumberValue()
                    );
                }
            }

            if (cell.getCellType() == CellType.STRING) {
                return cell.getStringCellValue().trim();
            }

            if (cell.getCellType() == CellType.NUMERIC) {
                return formatNumericCode(
                        cell.getNumericCellValue()
                );
            }

        } catch (Exception ignored) {
        }

        return "";
    }

    private String formatNumericCode(double value) {

        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return BigDecimal
                .valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String rowToText(
            Row row,
            FormulaEvaluator evaluator) {

        StringBuilder result =
                new StringBuilder();

        for (int c = 0; c < row.getLastCellNum(); c++) {

            String value =
                    readString(row, c, evaluator);

            if (!value.isBlank()) {
                result.append(value).append(' ');
            }
        }

        return result.toString();
    }

    private boolean isSalarySheet(Sheet sheet) {
        return "Employs Salaris"
                .equalsIgnoreCase(sheet.getSheetName());
    }

    private boolean isInvalidEmployeeRow(
            String code,
            String name) {

        String normalizedCode =
                normalizeCode(code);

        return normalizedCode.equals("0")
                || normalizedCode.equals("0.0")
                || normalizeName(name).equals("0")
                || normalizeName(name).equals("0.0");
    }

    private boolean isNumericEmployeeCode(String code) {

        try {
            Long.parseLong(
                    normalizeCode(code)
            );

            return true;

        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeCode(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\.0+$", "");
    }

    private String normalizeName(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BigDecimal parseNumber(String value) {

        if (value == null || value.isBlank()) {
            return ZERO;
        }

        try {

            String cleaned =
                    value
                            .trim()
                            .replace(",", "")
                            .replace("ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¹", "");

            return new BigDecimal(cleaned);

        } catch (Exception ex) {
            return ZERO;
        }
    }

    private int hoursToMinutes(BigDecimal hours) {

        if (hours == null) {
            return 0;
        }

        return hours
                .multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /*
     * ============================================================
     * DATA TYPES
     * ============================================================
     */

    private record SalaryRow(
            String companyCode,
            String employeeCode,
            String employeeName,
            BigDecimal ctc,
            BigDecimal gross) {
    }

    private static class SalarySource {

        private final List<SalaryRow> rows;

        private SalarySource(List<SalaryRow> rows) {
            this.rows = rows;
        }

        List<SalaryRow> rows() {
            return rows;
        }

        SalaryRow find(String employeeCode, String employeeName) {

            String code = normalizeLookupCode(employeeCode);
            String name = normalizeLookupName(employeeName);

            if (code.isBlank()) {
                return null;
            }

            List<SalaryRow> codeMatches = rows.stream()
                    .filter(r -> normalizeLookupCode(r.employeeCode())
                            .equalsIgnoreCase(code))
                    .toList();

            if (codeMatches.isEmpty()) {
                return null;
            }

            // Employee ID is the primary match.
            if (codeMatches.size() == 1) {
                return codeMatches.get(0);
            }

            // Duplicate ID: use employee name to disambiguate.
            List<SalaryRow> nameMatches = codeMatches.stream()
                    .filter(r -> namesCompatible(
                            r.employeeName(),
                            name))
                    .toList();

            if (nameMatches.size() == 1) {
                return nameMatches.get(0);
            }

            if (nameMatches.size() > 1) {
                throw new IllegalStateException(
                        "Ambiguous salary source match for "
                                + employeeCode
                                + " / "
                                + employeeName
                );
            }

            throw new IllegalStateException(
                    "Duplicate employee ID found in salary source, "
                            + "but employee name could not disambiguate: "
                            + employeeCode
                            + " / "
                            + employeeName
            );
        }

        SalaryRow find(
                String companyCode,
                String employeeCode,
                String employeeName) {

            String company = companyCode == null
                    ? ""
                    : companyCode.trim().toUpperCase(Locale.ROOT);

            String code = normalizeLookupCode(employeeCode);
            String name = normalizeLookupName(employeeName);

            List<SalaryRow> companyMatches = rows.stream()
                    .filter(r -> r.companyCode()
                            .equalsIgnoreCase(company))
                    .filter(r -> normalizeLookupCode(r.employeeCode())
                            .equalsIgnoreCase(code))
                    .toList();

            if (companyMatches.isEmpty()) {
                return null;
            }

            if (companyMatches.size() == 1) {
                return companyMatches.get(0);
            }

            List<SalaryRow> nameMatches = companyMatches.stream()
                    .filter(r -> namesCompatible(
                            r.employeeName(),
                            name))
                    .toList();

            if (nameMatches.size() == 1) {
                return nameMatches.get(0);
            }

            if (nameMatches.size() > 1) {
                throw new IllegalStateException(
                        "Ambiguous salary source match for "
                                + companyCode
                                + " / "
                                + employeeCode
                                + " / "
                                + employeeName
                );
            }

            throw new IllegalStateException(
                    "Duplicate employee ID found for company "
                            + companyCode
                            + ", but employee name could not "
                            + "disambiguate: "
                            + employeeCode
                            + " / "
                            + employeeName
            );
        }

        private static String normalizeLookupCode(String value) {
            if (value == null) {
                return "";
            }

            return value
                    .trim()
                    .replaceAll("\\.0+$", "")
                    .toUpperCase(Locale.ROOT);
        }

        private static String normalizeLookupName(String value) {
            if (value == null) {
                return "";
            }

            return value
                    .trim()
                    .replaceAll("\\s+", " ")
                    .toUpperCase(Locale.ROOT);
        }

        private static boolean namesCompatible(
                String salaryName,
                String payrollName) {

            String a = normalizeNameForComparison(salaryName);
            String b = normalizeNameForComparison(payrollName);

            if (a.isBlank() || b.isBlank()) {
                return false;
            }

            if (a.equals(b)) {
                return true;
            }

            String compactA = a.replaceAll("[^A-Z0-9]", "");
            String compactB = b.replaceAll("[^A-Z0-9]", "");

            if (compactA.equals(compactB)) {
                return true;
            }

            String wordsA = a.replace(".", " ");
            String wordsB = b.replace(".", " ");

            if (wordsA.equals(wordsB)) {
                return true;
            }

            return wordsA.endsWith(" " + wordsB)
                    || wordsB.endsWith(" " + wordsA);
        }

        private static String normalizeNameForComparison(String value) {
            if (value == null) {
                return "";
            }

            return value
                    .trim()
                    .replaceAll("\\s+", " ")
                    .toUpperCase(Locale.ROOT);
        }
    }
    private record PayrollSheet(
            String sheetName,
            int dataStartRow,
            Integer employeeCodeColumn,
            Integer employeeNameColumn,
            Integer presentDaysColumn,
            Integer actualWorkingHoursColumn,
            Integer payableHoursColumn,
            Integer lateCutColumn,
            Integer casualLeaveColumn,
            Integer payableDaysColumn,
            Integer chargePerDayColumn,
            Integer ctcColumn,
            Integer grossSalaryColumn,
            Integer earnedGrossColumn,
            Integer basicColumn,
            Integer earnedBasicColumn,
            Integer hraColumn,
            Integer conveyanceColumn,
            Integer medicalColumn,
            Integer othersColumn,
            Integer pfColumn,
            Integer esiColumn,
            Integer ptColumn,
            Integer finalPayColumn,
            int workingDays) {
    }
}



