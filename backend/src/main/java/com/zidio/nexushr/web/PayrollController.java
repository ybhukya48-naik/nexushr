package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.service.ExcelPayrollService;
import com.zidio.nexushr.service.PayrollPdfService;
import com.zidio.nexushr.service.PayrollService;
import com.zidio.nexushr.service.email.ResendEmailService;
import com.zidio.nexushr.web.dto.PayrollAutoComponentsResponse;
import com.zidio.nexushr.web.dto.PayrollRequest;
import com.zidio.nexushr.web.dto.PayrollResponse;
import com.zidio.nexushr.web.dto.PayslipResponse;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.web.dto.AttendanceImportResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollPdfService payrollPdfService;
    private final ResendEmailService resendEmailService;
    private final ExcelPayrollService excelPayrollService;
    private final AttendanceService attendanceService;

    public PayrollController(
            PayrollService payrollService,
            PayrollPdfService payrollPdfService,
            ResendEmailService resendEmailService,
            ExcelPayrollService excelPayrollService,
            @Lazy AttendanceService attendanceService) {

        this.payrollService = payrollService;
        this.payrollPdfService = payrollPdfService;
        this.resendEmailService = resendEmailService;
        this.excelPayrollService = excelPayrollService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<PayrollResponse> list() {

        return payrollService.findAll()
                .stream()
                .map(PayrollResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PayrollResponse getById(@PathVariable Long id) {

        return PayrollResponse.from(
                payrollService.findById(id)
        );
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(
            "@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)"
    )
    public List<PayrollResponse> listByEmployee(
            @PathVariable Long employeeId) {

        return payrollService.findByEmployee(employeeId)
                .stream()
                .map(PayrollResponse::from)
                .toList();
    }

    @GetMapping("/{id}/payslip")
    @PreAuthorize(
            "@authorizationService.canAccessPayroll(#id, authentication)"
    )
    public PayslipResponse payslip(@PathVariable Long id) {

        return payrollService.getPayslip(id);
    }

        @GetMapping("/auto-components")
        @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
        public PayrollAutoComponentsResponse autoComponents(
                        @RequestParam Long employeeId,
                        @RequestParam String payMonth) {

                return payrollService.autoComponents(employeeId, payMonth);
        }

    @GetMapping("/{id}/payslip/pdf")
    @PreAuthorize(
            "@authorizationService.canAccessPayroll(#id, authentication)"
    )
    public ResponseEntity<byte[]> payslipPdf(
            @PathVariable Long id) {

        PayrollRecord payroll =
                payrollService.findById(id);

        byte[] pdf =
                payrollPdfService.generatePayslip(payroll);

        String employeeCode =
                payroll.getEmployee().getEmployeeCode();

        String payMonth =
                payroll.getPayMonth();

        String filename =
                "CYOND-Payslip-"
                        + employeeCode
                        + "-"
                        + payMonth
                        + ".pdf";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{id}/payslip/email")
    @PreAuthorize(
            "@authorizationService.canAccessPayroll(#id, authentication)"
    )
    public ResponseEntity<String> emailPayslip(
            @PathVariable Long id) {

        PayrollRecord payroll =
                payrollService.findById(id);

        if (payroll.getEmployee() == null) {
            throw new IllegalStateException(
                    "Employee is not associated with this payroll record."
            );
        }

        String employeeEmail =
                payroll.getEmployee().getEmail();

        if (employeeEmail == null || employeeEmail.isBlank()) {
            throw new IllegalStateException(
                    "Employee email is not configured."
            );
        }

        byte[] pdf =
                payrollPdfService.generatePayslip(payroll);

        String employeeCode =
                payroll.getEmployee().getEmployeeCode();

        String payMonth =
                payroll.getPayMonth();

        String filename =
                "CYOND-Payslip-"
                        + employeeCode
                        + "-"
                        + payMonth
                        + ".pdf";

        String employeeName =
                payroll.getEmployee().getFullName();

        String subject =
                "Cyond Payslip - " + payMonth;

        String html =
                "<div style=\"font-family:Arial,sans-serif;line-height:1.6;\">"
                        + "<h2>Cyond HR - Payslip</h2>"
                        + "<p>Hello "
                        + employeeName
                        + ",</p>"
                        + "<p>Your payslip for <strong>"
                        + payMonth
                        + "</strong> is attached to this email.</p>"
                        + "<p>Employee Code: <strong>"
                        + employeeCode
                        + "</strong></p>"
                        + "<p>Regards,<br>Cyond HR</p>"
                        + "</div>";

        try {
            String resendId =
                    resendEmailService.sendHtmlEmailWithPdf(
                            employeeEmail,
                            subject,
                            html,
                            pdf,
                            filename
                    );

            return ResponseEntity.ok(
                    "Payslip emailed successfully. Resend ID: " + resendId
            );

        } catch (HttpClientErrorException.Forbidden ex) {

            return ResponseEntity
                    .status(403)
                    .body(
                            "Payslip email could not be sent. "
                                    + "Resend is currently in testing mode and "
                                    + "only allows emails to the Resend account email. "
                                    + "Verify a sending domain in Resend to email employees."
                    );

        } catch (HttpClientErrorException ex) {

            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(
                            "Payslip email could not be sent: "
                                    + ex.getResponseBodyAsString()
                    );
        }
    }

    @PostMapping
    public PayrollResponse create(
            @RequestBody PayrollRequest request) {

        return PayrollResponse.from(
                payrollService.create(request)
        );
    }

    @PostMapping("/{id}/mark-paid")
    public PayrollResponse markPaid(
            @PathVariable Long id) {

        return PayrollResponse.from(
                payrollService.markPaid(id)
        );
    }

    /**
     * Import salaries from a multi-sheet salary Excel workbook.
     * Calculates earned salary based on actual working hours/days,
     * syncs attendance records, and sends email notifications to employees.
     *
     * <p>POST /api/v1/payroll/import-excel
     * <p>Required role: HR, ADMIN, or MANAGER
     */
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("payMonth") String payMonth) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        if (payMonth == null || payMonth.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payMonth is required (YYYY-MM)");
        }

        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("nexushr_pay_", ".xlsx");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // A standard attendance sheet is not a multi-sheet salary workbook.
            if (!attendanceService.isSalaryWorkbook(tempPath)) {
                AttendanceImportResponse attResult = attendanceService.importMonthlyExcel(file, payMonth);
                return ResponseEntity.ok(Map.of(
                        "payMonth", payMonth,
                        "imported", attResult.importedCount(),
                        "updated", attResult.updatedCount(),
                        "skipped", attResult.skippedCount(),
                        "total", attResult.totalRows(),
                        "errors", attResult.errors(),
                        "message", "Attendance data processed (" + attResult.importedCount() + " new, "
                                + attResult.updatedCount() + " updated) and monthly salaries calculated."
                ));
            }

            List<PayrollRecord> imported = excelPayrollService.importPayroll(tempPath, payMonth);

            return ResponseEntity.ok(Map.of(
                    "payMonth", payMonth,
                    "imported", imported.size(),
                    "message", "Salary data imported and email notifications dispatched for " + imported.size() + " employee(s)."
            ));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the Excel file.");
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
            }
        }
    }
}


