package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.service.PayrollPdfService;
import com.zidio.nexushr.service.PayrollService;
import com.zidio.nexushr.service.email.ResendEmailService;
import com.zidio.nexushr.web.dto.PayrollRequest;
import com.zidio.nexushr.web.dto.PayrollResponse;
import com.zidio.nexushr.web.dto.PayslipResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollPdfService payrollPdfService;
    private final ResendEmailService resendEmailService;

    public PayrollController(
            PayrollService payrollService,
            PayrollPdfService payrollPdfService,
            ResendEmailService resendEmailService) {

        this.payrollService = payrollService;
        this.payrollPdfService = payrollPdfService;
        this.resendEmailService = resendEmailService;
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
}


