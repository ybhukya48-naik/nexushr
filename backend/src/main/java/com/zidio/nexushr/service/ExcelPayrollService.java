package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class ExcelPayrollService {

    private static final Logger log = LoggerFactory.getLogger(ExcelPayrollService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal NINE = BigDecimal.valueOf(9);

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    public ExcelPayrollService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            AttendanceRepository attendanceRepository,
            NotificationService notificationService) {

        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationService = notificationService;
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

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            SalarySource salarySource = readSalarySource(workbook, evaluator);

            List<PayrollRecord> created = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            YearMonth yearMonth = parseYearMonth(payMonth);

            for (Sheet sheet : workbook) {
                if (isSalarySheet(sheet)) {
                    continue;
                }

                PayrollSheet payrollSheet = detectPayrollSheet(sheet);
                if (payrollSheet == null) {
                    continue;
                }

                String companyHint = sheet.getSheetName().toUpperCase(Locale.ROOT).contains("CYOND")
                        ? "CYOND"
                        : "GORLE";

                for (int rowIndex = payrollSheet.dataStartRow();
                     rowIndex <= sheet.getLastRowNum();
                     rowIndex++) {

                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String employeeCode = readString(row, payrollSheet.employeeCodeColumn(), evaluator);
                    String employeeName = readString(row, payrollSheet.employeeNameColumn(), evaluator);

                    if (isBlank(employeeCode) || isBlank(employeeName)) {
                        continue;
                    }

                    if (isInvalidEmployeeRow(employeeCode, employeeName)) {
                        continue;
                    }

                    try {
                        SalaryRow salary = salarySource.find(companyHint, employeeCode, employeeName);
                        if (salary == null) {
                            salary = salarySource.find(employeeCode, employeeName);
                        }

                        BigDecimal sheetCtc = payrollSheet.ctcColumn() != null
                                ? value(row, payrollSheet.ctcColumn(), evaluator)
                                : ZERO;
                        BigDecimal sheetGross = payrollSheet.grossSalaryColumn() != null
                                ? value(row, payrollSheet.grossSalaryColumn(), evaluator)
                                : ZERO;

                        Employee employee = resolveEmployee(
                                employeeCode,
                                employeeName,
                                sheet.getSheetName(),
                                salary,
                                sheetCtc,
                                sheetGross
                        );

                        Optional<PayrollRecord> existingOpt = payrollRepository
                                .findByEmployee_IdAndPayMonth(employee.getId(), payMonth);

                        PayrollRecord payroll;
                        if (existingOpt.isPresent()) {
                            payroll = existingOpt.get();
                            updatePayrollRecord(payroll, row, payrollSheet, salary, evaluator);
                        } else {
                            payroll = buildPayrollRecord(employee, payMonth, row, payrollSheet, salary, evaluator);
                        }

                        PayrollRecord savedRecord = payrollRepository.save(payroll);
                        created.add(savedRecord);

                        // Attendance records are managed separately.
                        // Payroll import must not create or overwrite attendance.

                        // Send email notification to employee
                        sendSalaryNotification(employee, savedRecord, payMonth);

                    } catch (Exception ex) {
                        log.warn("Error processing row {} in sheet {}: {}", rowIndex + 1, sheet.getSheetName(), ex.getMessage());
                        errors.add("Sheet [" + sheet.getSheetName() + "], row [" + (rowIndex + 1)
                                + "], employee [" + employeeCode + " / " + employeeName + "]: " + ex.getMessage());
                    }
                }
            }

            if (created.isEmpty() && !errors.isEmpty()) {
                throw new IllegalStateException(
                        "Excel payroll import failed:\n" + String.join("\n", errors)
                );
            }

            return created;

        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read payroll Excel: " + ex.getMessage(), ex);
        }
    }

    private void syncAttendance(Employee employee, YearMonth yearMonth, BigDecimal actualHours, int workingDays) {
        if (employee == null || employee.getId() == null || yearMonth == null) {
            return;
        }

        try {
            int days = workingDays > 0 ? workingDays : 22;
            int totalHours = actualHours != null && actualHours.compareTo(ZERO) > 0
                    ? actualHours.intValue()
                    : days * 9;
            int dailyHours = Math.max(1, Math.min(12, totalHours / days));
            int remainingHours = totalHours % days;

            LocalDate cur = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();
            int dayCount = 0;

            while (!cur.isAfter(end) && dayCount < days) {
                if (cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    int hoursToday = dailyHours + (dayCount < remainingHours ? 1 : 0);
                    AttendanceRecord record = attendanceRepository
                            .findByEmployee_IdAndAttendanceDate(employee.getId(), cur)
                            .orElseGet(AttendanceRecord::new);

                    record.setEmployee(employee);
                    record.setAttendanceDate(cur);
                    record.setCheckInTime(cur.atTime(9, 0));
                    record.setCheckOutTime(cur.atTime(9 + hoursToday, 0));
                    record.setWorkMinutes(hoursToday * 60);
                    record.setLateArrivalMinutes(0);
                    record.setOvertimeMinutes(Math.max(0, (hoursToday - 9) * 60));

                    attendanceRepository.save(record);
                    dayCount++;
                }
                cur = cur.plusDays(1);
            }
        } catch (Exception ex) {
            log.warn("Failed to sync attendance for employee {}: {}", employee.getEmployeeCode(), ex.getMessage());
        }
    }

    private void sendSalaryNotification(Employee employee, PayrollRecord payroll, String payMonth) {
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            return;
        }

        try {
            String subject = "Salary Processed - " + payMonth;
            String message = "Dear " + employee.getFullName() + ",\n\n"
                    + "Your salary for " + payMonth + " has been calculated and processed.\n"
                    + "Worked Hours: " + (payroll.getActualHours() != null ? payroll.getActualHours() : 0) + " hrs\n"
                    + "Earned Gross: â‚¹" + payroll.getEarnedGross() + "\n"
                    + "Deductions: â‚¹" + payroll.getDeductions() + "\n"
                    + "Net Salary: â‚¹" + payroll.getNetSalary() + "\n\n"
                    + "Your payslip is available on the Cyond HR platform.\n\n"
                    + "Best regards,\nCyond HR Team";

            notificationService.sendNotification(
                    employee.getId(),
                    subject,
                    message,
                    NotificationType.GENERAL,
                    NotificationChannel.EMAIL,
                    employee.getEmail(),
                    employee.getPhone()
            );
        } catch (Exception ex) {
            log.warn("Failed to send salary notification email to {}: {}", employee.getEmail(), ex.getMessage());
        }
    }

    private YearMonth parseYearMonth(String payMonth) {
        try {
            return YearMonth.parse(payMonth);
        } catch (Exception ex) {
            return YearMonth.now();
        }
    }

    private Employee resolveEmployee(
            String employeeCode,
            String employeeName,
            String sheetName,
            SalaryRow salary,
            BigDecimal rowCtc,
            BigDecimal rowGross) {

        String code = normalizeCode(employeeCode);
        String name = employeeName != null ? employeeName.trim() : "";

        // 1. Exact employee code match
        Optional<Employee> found = employeeRepository.findByEmployeeCodeIgnoreCase(code);
        if (found.isPresent()) {
            return found.get();
        }

        // 2. Numeric code variations with prefixes (e.g., 11 -> CY-EMP-011)
        try {
            int num = Integer.parseInt(code);
            String padded3 = String.format("%03d", num);
            found = employeeRepository.findByEmployeeCodeIgnoreCase("CY-EMP-" + padded3);
            if (found.isPresent()) return found.get();

            found = employeeRepository.findByEmployeeCodeIgnoreCase("CY-EMP-" + code);
            if (found.isPresent()) return found.get();

            found = employeeRepository.findByEmployeeCodeIgnoreCase("GG-EMP-" + padded3);
            if (found.isPresent()) return found.get();

            found = employeeRepository.findByEmployeeCodeIgnoreCase("GG-EMP-" + code);
            if (found.isPresent()) return found.get();

            found = employeeRepository.findByEmployeeCodeIgnoreCase("E" + code);
            if (found.isPresent()) return found.get();
        } catch (Exception ignored) {
        }

        // 3. Name lookup in database
        if (!name.isBlank()) {
            found = employeeRepository.findByFullNameIgnoreCase(name);
            if (found.isPresent()) {
                return found.get();
            }

            String normTarget = normalizeName(name);
            for (Employee e : employeeRepository.findAll()) {
                if (normalizeName(e.getFullName()).equalsIgnoreCase(normTarget)
                        || SalarySource.namesCompatible(e.getFullName(), name)) {
                    return e;
                }
            }
        }

        // 4. If not found in DB, auto-create complete valid employee record
        String companyCode = sheetName.toUpperCase(Locale.ROOT).contains("CYOND") ? "CYOND" : "GORLE";
        Company company = companyRepository.findByCodeIgnoreCase(companyCode)
                .orElseGet(() -> {
                    Company c = new Company();
                    c.setCode(companyCode);
                    c.setName(companyCode.equalsIgnoreCase("CYOND") ? "CYOND" : "GORLE GROUP");
                    c.setTagline(companyCode.equalsIgnoreCase("CYOND")
                            ? "Waterproofing Diagnosis & Repair Experts"
                            : "STRUCTURAL ENGINEERING");
                    c.setLogoPath(companyCode.equalsIgnoreCase("CYOND")
                            ? "/images/cyond-logo.jpeg"
                            : "/images/gorle-group-logo.jpeg");
                    c.setActive(true);
                    return companyRepository.save(c);
                });

        Employee newEmp = new Employee();
        String assignedCode = code.isBlank() ? ("EMP-" + System.currentTimeMillis()) : code;
        if (employeeRepository.existsByEmployeeCode(assignedCode)) {
            assignedCode = (companyCode.equals("CYOND") ? "CY-EMP-" : "GG-EMP-") + assignedCode;
        }
        newEmp.setEmployeeCode(assignedCode);
        newEmp.setFullName(name.isBlank() ? assignedCode : name);
        newEmp.setCompany(company);

        String cleanCode = assignedCode.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        String safeEmail = cleanCode + "@" + (companyCode.equals("CYOND") ? "cyond.com" : "gorlegroup.com");
        if (employeeRepository.existsByEmail(safeEmail)) {
            safeEmail = cleanCode + "." + System.currentTimeMillis() + "@" + (companyCode.equals("CYOND") ? "cyond.com" : "gorlegroup.com");
        }
        newEmp.setEmail(safeEmail);
        newEmp.setPassword("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH");
        newEmp.setRoleType(RoleType.EMPLOYEE);
        newEmp.setDepartment(sheetName);
        newEmp.setDesignation("Staff");
        newEmp.setJoiningDate(LocalDate.of(2026, 1, 1));

        BigDecimal base = ZERO;
        if (rowGross != null && rowGross.compareTo(ZERO) > 0) {
            base = rowGross;
        } else if (salary != null && salary.gross() != null && salary.gross().compareTo(ZERO) > 0) {
            base = salary.gross();
        } else if (salary != null && salary.ctc() != null && salary.ctc().compareTo(ZERO) > 0) {
            base = salary.ctc().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        } else if (rowCtc != null && rowCtc.compareTo(ZERO) > 0) {
            base = rowCtc.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        } else {
            base = BigDecimal.valueOf(30000);
        }
        newEmp.setBaseSalary(base);
        newEmp.setActive(true);
        newEmp.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        return employeeRepository.save(newEmp);
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

        updatePayrollRecord(payroll, row, sheet, salary, evaluator);
        return payroll;
    }

    private void updatePayrollRecord(
            PayrollRecord payroll,
            Row row,
            PayrollSheet sheet,
            SalaryRow salary,
            FormulaEvaluator evaluator) {

        BigDecimal sheetCtc = value(row, sheet.ctcColumn(), evaluator);
        BigDecimal sheetGross = value(row, sheet.grossSalaryColumn(), evaluator);

        BigDecimal ctc = sheetCtc.compareTo(ZERO) > 0
                ? sheetCtc
                : salary != null ? salary.ctc() : ZERO;

        BigDecimal grossSalary = sheetGross.compareTo(ZERO) > 0
                ? sheetGross
                : salary != null ? salary.gross() : ZERO;

        if (grossSalary.compareTo(ZERO) <= 0 && ctc.compareTo(ZERO) > 0) {
            grossSalary = ctc.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        }

        if (ctc.compareTo(ZERO) <= 0 && grossSalary.compareTo(ZERO) > 0) {
            ctc = grossSalary.multiply(BigDecimal.valueOf(12));
        }

        if (grossSalary.compareTo(ZERO) <= 0 && payroll.getEmployee().getBaseSalary() != null) {
            grossSalary = payroll.getEmployee().getBaseSalary();
            ctc = grossSalary.multiply(BigDecimal.valueOf(12));
        }

        payroll.setCtc(ctc);
        payroll.setGrossSalary(grossSalary);

        BigDecimal actualWorkingHours =
                "CYOND NDT".equalsIgnoreCase(sheet.sheetName())
                        ? durationHoursValue(row, sheet.actualWorkingHoursColumn(), evaluator)
                        : value(row, sheet.actualWorkingHoursColumn(), evaluator);
        BigDecimal casualLeave = value(row, sheet.casualLeaveColumn(), evaluator);
        BigDecimal chargePerDay = value(row, sheet.chargePerDayColumn(), evaluator);
        BigDecimal earnedGross = value(row, sheet.earnedGrossColumn(), evaluator);
        BigDecimal basic = value(row, sheet.basicColumn(), evaluator);
        BigDecimal hra = value(row, sheet.hraColumn(), evaluator);
        BigDecimal conveyance = value(row, sheet.conveyanceColumn(), evaluator);
        BigDecimal medical = value(row, sheet.medicalColumn(), evaluator);
        BigDecimal others = value(row, sheet.othersColumn(), evaluator);
        BigDecimal pf = value(row, sheet.pfColumn(), evaluator);
        BigDecimal esi = value(row, sheet.esiColumn(), evaluator);
        BigDecimal pt = value(row, sheet.ptColumn(), evaluator);

        payroll.setWorkingDays(sheet.workingDays());
        payroll.setRequiredHours(NINE);
        payroll.setActualHours(actualWorkingHours);
        payroll.setCasualLeave(casualLeave);
        payroll.setOtherLeave(ZERO);
        payroll.setChargePerDay(chargePerDay);
        payroll.setEarnedGross(earnedGross);

        payroll.setBasicSalary(
                basic.compareTo(ZERO) > 0
                        ? basic
                        : grossSalary.multiply(BigDecimal.valueOf(0.61))
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
        payroll.setTaxAmount(ZERO);
        payroll.setLeaveDeduction(ZERO);
        payroll.setShortHoursDeduction(ZERO);
        payroll.setOtherDeductions(ZERO);

        BigDecimal totalDeductions = pf.add(esi).add(pt);
        payroll.setDeductions(totalDeductions);

        // Safe Net Salary calculation
        if (sheet.finalPayColumn() != null && sheet.finalPayColumn() >= 0) {
            BigDecimal finalPay = value(row, sheet.finalPayColumn(), evaluator);
            if (finalPay.compareTo(ZERO) > 0) {
                payroll.setNetSalary(finalPay);
            } else if (earnedGross.compareTo(ZERO) > 0) {
                payroll.setNetSalary(earnedGross.subtract(totalDeductions));
            } else {
                payroll.setNetSalary(grossSalary.subtract(totalDeductions));
            }
        } else if (earnedGross.compareTo(ZERO) > 0) {
            payroll.setNetSalary(earnedGross.subtract(totalDeductions));
        } else {
            payroll.setNetSalary(grossSalary.subtract(totalDeductions));
        }

        int workedMinutes = hoursToMinutes(actualWorkingHours);
        int expectedMinutes = sheet.workingDays() * 9 * 60;

        payroll.setExpectedWorkMinutes(expectedMinutes);
        payroll.setWorkedMinutes(workedMinutes);
        payroll.setShortfallMinutes(Math.max(0, expectedMinutes - workedMinutes));
        payroll.setOvertimeMinutes(0);
    }

    private BigDecimal durationHoursValue(
            Row row,
            Integer column,
            FormulaEvaluator evaluator) {

        if (row == null || column == null || column < 0) {
            return ZERO;
        }

        Cell cell = row.getCell(column);
        if (cell == null) {
            return ZERO;
        }

        try {
            CellValue cellValue = evaluator.evaluate(cell);

            if (cellValue == null) {
                return parseDurationHours(cell.toString());
            }

            if (cellValue.getCellType() == CellType.NUMERIC) {
                double raw = cellValue.getNumberValue();
                String format = cell.getCellStyle().getDataFormatString();

                if (format != null) {
                    String normalizedFormat = format.toLowerCase(Locale.ROOT);

                    if (normalizedFormat.contains("[h]")
                            || normalizedFormat.contains("h:mm")) {
                        raw *= 24.0;
                    }
                }

                return BigDecimal.valueOf(raw)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            if (cellValue.getCellType() == CellType.STRING) {
                return parseDurationHours(cellValue.getStringValue());
            }

            return ZERO;
        } catch (Exception ex) {
            return parseDurationHours(cell.toString());
        }
    }

    private BigDecimal parseDurationHours(String text) {
        if (text == null || text.isBlank()) {
            return ZERO;
        }

        String normalized = text.trim();

        try {
            if (normalized.contains(":")) {
                String[] parts = normalized.split(":");

                if (parts.length == 2 || parts.length == 3) {
                    BigDecimal hours = new BigDecimal(parts[0]);
                    BigDecimal minutes = new BigDecimal(parts[1]);

                    BigDecimal seconds = parts.length == 3
                            ? new BigDecimal(parts[2])
                            : ZERO;

                    return hours
                            .add(minutes.divide(
                                    BigDecimal.valueOf(60),
                                    8,
                                    RoundingMode.HALF_UP))
                            .add(seconds.divide(
                                    BigDecimal.valueOf(3600),
                                    8,
                                    RoundingMode.HALF_UP))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            return parseNumber(normalized);
        } catch (Exception ex) {
            return ZERO;
        }
    }
    private SalarySource readSalarySource(
            Workbook workbook,
            FormulaEvaluator evaluator) {

        Sheet sheet = workbook.getSheet("Employs Salaris");
        if (sheet == null) {
            return new SalarySource(Collections.emptyList());
        }

        List<SalaryRow> rows = new ArrayList<>();
        String currentCompany = "GGEA";

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            String id = readString(row, 1, evaluator);
            String name = readString(row, 3, evaluator);
            String ctcText = readString(row, 4, evaluator);
            String grossText = readString(row, 5, evaluator);

            if (id.equalsIgnoreCase("ID No") || name.equalsIgnoreCase("Name")) {
                continue;
            }

            String completeRow = rowToText(row, evaluator).toUpperCase(Locale.ROOT);
            if (completeRow.contains("CYOND")) {
                currentCompany = "CYOND";
            }

            if (isBlank(id) || isBlank(name)) {
                continue;
            }

            if (!isNumericEmployeeCode(id)) {
                continue;
            }

            BigDecimal ctc = parseNumber(ctcText);
            BigDecimal gross = parseNumber(grossText);

            if (ctc.compareTo(ZERO) <= 0 && gross.compareTo(ZERO) <= 0) {
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

    private PayrollSheet detectPayrollSheet(Sheet sheet) {
        if ("CYOND NDT".equalsIgnoreCase(sheet.getSheetName())) {
            return detectCyondNdtSheet(sheet);
        }

        int headerRowIndex = -1;
        Map<String, Integer> headers = new HashMap<>();

        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Map<String, Integer> current = readHeaders(row);
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

        Integer employeeCode = first(headers, "EMPLOYEE CODE", "EMPLOYEE CODE ");
        Integer employeeName = first(headers, "EMPLOYEE NAME", "NAME");
        Integer presentDays = first(headers, "TOTAL PRESENT DAYS");
        Integer actualWorkingHours = first(headers, "ACTUAL WORKING HOURS");
        Integer payableHours = first(headers, "PAYABLE HOURS", "NET PAYABLE HOURS");
        Integer lateCut = first(headers, "LATE COME CUTT (IN DAYS)", "LATE COME CUTT (IN HOURS)");
        Integer casualLeave = first(headers, "CL");
        Integer payableDays = first(headers, "NET PAYABLE DAYS");
        Integer chargePerDay = first(headers, "CHARGE PER DAY");
        Integer ctcColumn = first(headers, "CTC");
        Integer grossSalaryColumn = first(headers, "GROSS SALARY", "GROSS");
        Integer earnedGross = first(headers, "EARNED GROSS");
        Integer basic = first(headers, "BASIC");
        Integer earnedBasic = first(headers, "EARNED BASIC");
        Integer hra = first(headers, "HRA");
        Integer conveyance = first(headers, "CONVEYANCE");
        Integer medical = first(headers, "MEDICAL");
        Integer others = first(headers, "OTHERS");
        Integer pf = first(headers, "PF");
        Integer esi = first(headers, "ESI");
        Integer pt = first(headers, "PT");
        Integer finalPay = first(headers, "FINAL PAY");

        if (employeeCode == null || employeeName == null || payableDays == null || chargePerDay == null || earnedGross == null) {
            return null;
        }

        int workingDays = detectWorkingDays(sheet, headerRowIndex);

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
        int workingDays = detectWorkingDays(sheet, 9);

        return new PayrollSheet(
                sheet.getSheetName(),
                9,
                25,     // Z Employee Code
                26,     // AA Employee Name
                27,     // AB Total Present Days
                28,     // AC Actual Working Hours
                34,     // AI Payable Hours
                36,     // AK Late Come Cutt
                39,     // AN CL
                40,     // AO Net Payable Days
                41,     // AP Charge Per Day
                42,     // AQ CTC
                43,     // AR Gross Salary
                44,     // AS Earned Gross
                45,     // AT Basic
                46,     // AU Earned Basic
                47,     // AV HRA
                48,     // AW Conveyance
                49,     // AX Medical
                50,     // AY Others
                51,     // AZ PF
                52,     // BA ESI
                53,     // BB PT
                57,     // BF Final Pay
                workingDays
        );
    }

    private int detectWorkingDays(Sheet sheet, int headerRow) {
        for (int i = 0; i < headerRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            for (Cell cell : row) {
                String text = cell.toString().toUpperCase(Locale.ROOT);
                if (text.contains("NO OF DAYS") || text.contains("WORKING DAYS")) {
                    for (int c = cell.getColumnIndex() + 1; c <= cell.getColumnIndex() + 4; c++) {
                        Cell valCell = row.getCell(c);
                        if (valCell != null && valCell.getCellType() == CellType.NUMERIC) {
                            int d = (int) valCell.getNumericCellValue();
                            if (d >= 20 && d <= 31) {
                                return d;
                            }
                        }
                    }
                }
            }
        }
        return 26;
    }

    private Map<String, Integer> readHeaders(Row row) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                String text = cell.toString().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (!text.isEmpty()) {
                    map.put(text, c);
                }
            }
        }
        return map;
    }

    private Integer first(Map<String, Integer> map, String... keys) {
        for (String k : keys) {
            Integer col = map.get(k.toUpperCase(Locale.ROOT));
            if (col != null) {
                return col;
            }
        }
        return null;
    }

    private boolean hasAny(Map<String, Integer> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String readString(Row row, Integer column, FormulaEvaluator evaluator) {
        if (row == null || column == null || column < 0) {
            return "";
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        try {
            CellValue val = evaluator.evaluate(cell);
            if (val == null) {
                return cell.toString().trim();
            }
            return switch (val.getCellType()) {
                case STRING -> val.getStringValue().trim();
                case NUMERIC -> {
                    double num = val.getNumberValue();
                    if (num == Math.floor(num)) {
                        yield String.valueOf((long) num);
                    }
                    yield String.valueOf(num);
                }
                case BOOLEAN -> String.valueOf(val.getBooleanValue());
                default -> "";
            };
        } catch (Exception ex) {
            return cell.toString().trim();
        }
    }

    private BigDecimal value(Row row, Integer column, FormulaEvaluator evaluator) {
        if (row == null || column == null || column < 0) {
            return ZERO;
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            return ZERO;
        }
        try {
            CellValue val = evaluator.evaluate(cell);
            if (val == null) {
                return parseNumber(cell.toString());
            }
            if (val.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(val.getNumberValue()).setScale(2, RoundingMode.HALF_UP);
            }
            if (val.getCellType() == CellType.STRING) {
                return parseNumber(val.getStringValue());
            }
            return ZERO;
        } catch (Exception ex) {
            return parseNumber(cell.toString());
        }
    }

    private String rowToText(Row row, FormulaEvaluator evaluator) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String s = readString(row, c, evaluator);
            if (!s.isEmpty()) {
                sb.append(s).append(' ');
            }
        }
        return sb.toString();
    }

    private boolean isSalarySheet(Sheet sheet) {
        return "Employs Salaris".equalsIgnoreCase(sheet.getSheetName());
    }

    private boolean isInvalidEmployeeRow(String code, String name) {
        String c = normalizeCode(code);
        String n = normalizeName(name);
        return c.equals("0") || c.equals("0.0") || n.equals("0") || n.equals("0.0");
    }

    private boolean isNumericEmployeeCode(String code) {
        try {
            Long.parseLong(normalizeCode(code));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\.0+$", "");
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BigDecimal parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        try {
            String cleaned = value.trim().replace(",", "").replace("â‚¹", "");
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return ZERO;
        }
    }

    private int hoursToMinutes(BigDecimal hours) {
        if (hours == null) {
            return 0;
        }
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

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

        SalaryRow find(String employeeCode, String employeeName) {
            String code = normalizeCode(employeeCode);
            String name = normalizeName(employeeName);

            if (code.isBlank()) {
                return null;
            }

            List<SalaryRow> codeMatches = rows.stream()
                    .filter(r -> normalizeCode(r.employeeCode()).equalsIgnoreCase(code))
                    .toList();

            if (codeMatches.size() == 1) {
                return codeMatches.get(0);
            }

            if (!codeMatches.isEmpty()) {
                for (SalaryRow r : codeMatches) {
                    if (namesCompatible(r.employeeName(), name)) {
                        return r;
                    }
                }
                return codeMatches.get(0);
            }

            for (SalaryRow r : rows) {
                if (namesCompatible(r.employeeName(), name)) {
                    return r;
                }
            }

            return null;
        }

        SalaryRow find(String companyCode, String employeeCode, String employeeName) {
            String company = companyCode == null ? "" : companyCode.trim().toUpperCase(Locale.ROOT);
            String code = normalizeCode(employeeCode);
            String name = normalizeName(employeeName);

            List<SalaryRow> companyMatches = rows.stream()
                    .filter(r -> r.companyCode().equalsIgnoreCase(company))
                    .filter(r -> normalizeCode(r.employeeCode()).equalsIgnoreCase(code))
                    .toList();

            if (companyMatches.size() == 1) {
                return companyMatches.get(0);
            }

            if (!companyMatches.isEmpty()) {
                for (SalaryRow r : companyMatches) {
                    if (namesCompatible(r.employeeName(), name)) {
                        return r;
                    }
                }
                return companyMatches.get(0);
            }

            return find(employeeCode, employeeName);
        }

        public static boolean namesCompatible(String salaryName, String payrollName) {
            String a = normalizeName(salaryName);
            String b = normalizeName(payrollName);

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

            return wordsA.endsWith(" " + wordsB) || wordsB.endsWith(" " + wordsA)
                    || wordsA.startsWith(wordsB + " ") || wordsB.startsWith(wordsA + " ");
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





