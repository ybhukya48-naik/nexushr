package com.zidio.nexushr.web;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.GenderType;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;

    public DashboardController(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            PayrollRepository payrollRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollRepository = payrollRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        long totalEmployees = employeeRepository.count();

        long activeEmployees =
                employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        long pendingOnboarding =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.PENDING_ONBOARDING);

        long pendingOffboarding =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.OFFBOARDING);

        long attendanceEvents = attendanceRepository.count();
        long leaveRequests = leaveRequestRepository.count();

        long pendingLeaveRequests =
                leaveRequestRepository.countByStatus(LeaveStatus.PENDING);

        long payrollRecords = payrollRepository.count();

        long paidPayrollRecords =
                payrollRepository.countByStatus(PayrollStatus.PAID);

        return Map.of(
                "totalEmployees", totalEmployees,
                "activeEmployees", activeEmployees,
                "pendingOnboarding", pendingOnboarding,
                "pendingOffboarding", pendingOffboarding,
                "attendanceEvents", attendanceEvents,
                "leaveRequests", leaveRequests,
                "pendingLeaveRequests", pendingLeaveRequests,
                "payrollRecords", payrollRecords,
                "paidPayrollRecords", paidPayrollRecords
        );
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        List<Employee> employees = employeeRepository.findAll();

        Map<String, Long> employeesByDepartment = new LinkedHashMap<>();

        for (Object[] row : employeeRepository.countEmployeesByDepartment()) {
            employeesByDepartment.put(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue()
            );
        }

        Map<String, Long> employeesByRole = new LinkedHashMap<>();

        for (Object[] row : employeeRepository.countEmployeesByRole()) {
            employeesByRole.put(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue()
            );
        }

        Map<String, Long> hiringTrends =
                calculateHiringTrends(employees);

        Map<String, Long> genderRatio =
                calculateGenderRatio(employees);

        long totalEmployees = employeeRepository.count();

        long activeEmployees =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.ACTIVE);

        long offboardedEmployees =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.OFFBOARDED);

        double attritionRate = totalEmployees == 0
                ? 0.0
                : (offboardedEmployees * 100.0) / totalEmployees;

        return Map.of(
                "employeesByDepartment", employeesByDepartment,
                "employeesByRole", employeesByRole,
                "hiringTrends", hiringTrends,
                "genderRatio", genderRatio,
                "totalEmployees", totalEmployees,
                "activeEmployees", activeEmployees,
                "offboardedEmployees", offboardedEmployees,
                "attritionRate", round(attritionRate)
        );
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        Map<String, Object> analytics = analytics();

        long totalEmployees = employeeRepository.count();

        long activeEmployees =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.ACTIVE);

        long pendingOnboarding =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.PENDING_ONBOARDING);

        long pendingOffboarding =
                employeeRepository.countByLifecycleStatus(
                        EmployeeLifecycleStatus.OFFBOARDING);

        long attendanceEvents = attendanceRepository.count();
        long leaveRequests = leaveRequestRepository.count();

        long pendingLeaveRequests =
                leaveRequestRepository.countByStatus(LeaveStatus.PENDING);

        long payrollRecords = payrollRepository.count();

        long paidPayrollRecords =
                payrollRepository.countByStatus(PayrollStatus.PAID);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            Sheet summarySheet = workbook.createSheet("Summary");

            Row summaryHeader = summarySheet.createRow(0);
            summaryHeader.createCell(0).setCellValue("Metric");
            summaryHeader.createCell(1).setCellValue("Value");

            Object[][] summaryData = {
                    {"Total Employees", totalEmployees},
                    {"Active Employees", activeEmployees},
                    {"Pending Onboarding", pendingOnboarding},
                    {"Pending Offboarding", pendingOffboarding},
                    {"Attendance Events", attendanceEvents},
                    {"Leave Requests", leaveRequests},
                    {"Pending Leave Requests", pendingLeaveRequests},
                    {"Payroll Records", payrollRecords},
                    {"Paid Payroll Records", paidPayrollRecords},
                    {"Offboarded Employees", analytics.get("offboardedEmployees")},
                    {"Attrition Rate (%)", analytics.get("attritionRate")}
            };

            for (int i = 0; i < summaryData.length; i++) {
                Row row = summarySheet.createRow(i + 1);
                row.createCell(0).setCellValue(
                        String.valueOf(summaryData[i][0])
                );
                row.createCell(1).setCellValue(
                        ((Number) summaryData[i][1]).doubleValue()
                );
            }

            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            writeMapSheet(
                    workbook,
                    "Department Analytics",
                    "Department",
                    "Employee Count",
                    castLongMap(analytics.get("employeesByDepartment"))
            );

            writeMapSheet(
                    workbook,
                    "Role Analytics",
                    "Role",
                    "Employee Count",
                    castLongMap(analytics.get("employeesByRole"))
            );

            writeMapSheet(
                    workbook,
                    "Hiring Trends",
                    "Month",
                    "Hires",
                    castLongMap(analytics.get("hiringTrends"))
            );

            writeMapSheet(
                    workbook,
                    "Gender Ratio",
                    "Gender",
                    "Employee Count",
                    castLongMap(analytics.get("genderRatio"))
            );

            workbook.write(outputStream);

            byte[] file = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(
                    MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
            );

            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("nexushr-dashboard.xlsx")
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(file);
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() throws IOException {
        Map<String, Object> analytics = analytics();

        try (ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            Document document = new Document();

            try {
                PdfWriter.getInstance(document, outputStream);
                document.open();

                document.add(
                        new Paragraph("NexusHR Dashboard Report")
                );

                document.add(new Paragraph(" "));

                PdfPTable summaryTable = new PdfPTable(2);
                summaryTable.setWidthPercentage(100);

                summaryTable.addCell("Metric");
                summaryTable.addCell("Value");

                addPdfRow(
                        summaryTable,
                        "Total Employees",
                        ((Number) analytics.get("totalEmployees")).doubleValue()
                );

                addPdfRow(
                        summaryTable,
                        "Active Employees",
                        ((Number) analytics.get("activeEmployees")).doubleValue()
                );

                addPdfRow(
                        summaryTable,
                        "Offboarded Employees",
                        ((Number) analytics.get("offboardedEmployees")).doubleValue()
                );

                addPdfRow(
                        summaryTable,
                        "Attrition Rate (%)",
                        ((Number) analytics.get("attritionRate")).doubleValue()
                );

                document.add(new Paragraph("Summary"));
                document.add(summaryTable);
                document.add(new Paragraph(" "));

                addPdfMapTable(
                        document,
                        "Department Analytics",
                        "Department",
                        "Employee Count",
                        castLongMap(analytics.get("employeesByDepartment"))
                );

                addPdfMapTable(
                        document,
                        "Role Analytics",
                        "Role",
                        "Employee Count",
                        castLongMap(analytics.get("employeesByRole"))
                );

                addPdfMapTable(
                        document,
                        "Hiring Trends",
                        "Month",
                        "Hires",
                        castLongMap(analytics.get("hiringTrends"))
                );

                addPdfMapTable(
                        document,
                        "Gender Ratio",
                        "Gender",
                        "Employee Count",
                        castLongMap(analytics.get("genderRatio"))
                );

                document.close();

            } catch (DocumentException exception) {
                throw new IOException(
                        "Unable to generate NexusHR PDF report",
                        exception
                );
            }

            byte[] file = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_PDF);

            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("nexushr-dashboard.pdf")
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(file);
        }
    }

    private Map<String, Long> countByDepartment(
            List<Employee> employees) {

        Map<String, Long> result = new LinkedHashMap<>();

        employees.stream()
                .map(Employee::getDepartment)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .forEach(department ->
                        result.put(
                                department,
                                employees.stream()
                                        .filter(employee ->
                                                department.equals(
                                                        employee.getDepartment()))
                                        .count()
                        )
                );

        return result;
    }

    private Map<String, Long> countByRole(
            List<Employee> employees) {

        Map<String, Long> result = new LinkedHashMap<>();

        employees.stream()
                .map(Employee::getRoleType)
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .forEach(role ->
                        result.put(
                                role.name(),
                                employees.stream()
                                        .filter(employee ->
                                                role.equals(
                                                        employee.getRoleType()))
                                        .count()
                        )
                );

        return result;
    }

    private Map<String, Long> calculateHiringTrends(
            List<Employee> employees) {

        Map<String, Long> result = new LinkedHashMap<>();

        employees.stream()
                .map(Employee::getJoiningDate)
                .filter(value -> value != null)
                .map(YearMonth::from)
                .distinct()
                .sorted()
                .forEach(month ->
                        result.put(
                                month.toString(),
                                employees.stream()
                                        .filter(employee ->
                                                employee.getJoiningDate() != null
                                                        && YearMonth.from(
                                                        employee.getJoiningDate())
                                                        .equals(month))
                                        .count()
                        )
                );

        return result;
    }

    private Map<String, Long> calculateGenderRatio(
            List<Employee> employees) {

        Map<String, Long> result = new LinkedHashMap<>();

        for (GenderType gender : GenderType.values()) {
            long count = employees.stream()
                    .filter(employee -> gender.equals(employee.getGender()))
                    .count();

            if (count > 0) {
                result.put(gender.name(), count);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> castLongMap(Object value) {
        return (Map<String, Long>) value;
    }

    private void writeMapSheet(
            Workbook workbook,
            String sheetName,
            String keyHeader,
            String valueHeader,
            Map<String, Long> data) {

        Sheet sheet = workbook.createSheet(sheetName);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(keyHeader);
        header.createCell(1).setCellValue(valueHeader);

        int index = 1;

        for (Map.Entry<String, Long> entry : data.entrySet()) {
            Row row = sheet.createRow(index++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addPdfMapTable(
            Document document,
            String title,
            String keyHeader,
            String valueHeader,
            Map<String, Long> data)
            throws DocumentException {

        document.add(new Paragraph(title));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        table.addCell(keyHeader);
        table.addCell(valueHeader);

        for (Map.Entry<String, Long> entry : data.entrySet()) {
            table.addCell(entry.getKey());
            table.addCell(String.valueOf(entry.getValue()));
        }

        document.add(table);
        document.add(new Paragraph(" "));
    }

    private void addPdfRow(
            PdfPTable table,
            String metric,
            double value) {

        table.addCell(metric);
        table.addCell(String.valueOf(value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
