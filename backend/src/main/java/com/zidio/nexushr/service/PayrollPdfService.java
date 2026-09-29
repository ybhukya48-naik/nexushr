package com.zidio.nexushr.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PayrollRecord;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PayrollPdfService {

    private static final Color BORDER =
            new Color(210, 214, 220);

    private static final Color LIGHT_BACKGROUND =
            new Color(247, 248, 250);

    private static final Color HEADER_BACKGROUND =
            new Color(20, 38, 58);

    private static final Color NET_BACKGROUND =
            new Color(235, 245, 239);

    private static final Font COMPANY_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    18
            );

    private static final Font TAGLINE_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA,
                    8
            );

    private static final Font TITLE_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    16
            );

    private static final Font SECTION_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    10
            );

    private static final Font NORMAL_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA,
                    8
            );

    private static final Font BOLD_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    8
            );

    private static final Font NET_FONT =
            FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    13
            );

    public byte[] generatePayslip(PayrollRecord payroll) {

        try {
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            /*
             * Single-page A4 payslip.
             * No CYOND cover page is added.
             */
            Document document =
                    new Document(
                            PageSize.A4,
                            36,
                            36,
                            30,
                            30
                    );

            PdfWriter.getInstance(
                    document,
                    outputStream
            );

            document.open();

            Employee employee =
                    payroll.getEmployee();

            String employeeCode =
                    safe(employee.getEmployeeCode());

            String employeeName =
                    safe(employee.getFullName());

            String payMonth =
                    safe(payroll.getPayMonth());

            String paymentStatus =
                    payroll.getStatus() == null
                            ? "N/A"
                            : payroll.getStatus().name();

            /*
             * =====================================================
             * CYOND HEADER
             * =====================================================
             */
            PdfPTable header =
                    new PdfPTable(3);

            header.setWidthPercentage(100);

            header.setWidths(
                    new float[]{
                            22,
                            53,
                            25
                    }
            );

            header.setSpacingAfter(7);

            /*
             * Actual CYOND logo.
             */
            PdfPCell logoCell =
                    new PdfPCell();

            logoCell.setBorder(
                    Rectangle.NO_BORDER
            );

            logoCell.setVerticalAlignment(
                    Element.ALIGN_MIDDLE
            );

            logoCell.setPadding(0);

            try (InputStream logoStream =
                         getClass().getResourceAsStream(
                                 "/images/cyond-logo.jpeg"
                         )) {

                if (logoStream == null) {
                    throw new IllegalStateException(
                            "CYOND logo not found: /images/cyond-logo.jpeg"
                    );
                }

                byte[] logoBytes =
                        logoStream.readAllBytes();

                com.lowagie.text.Image logo =
                        com.lowagie.text.Image.getInstance(
                                logoBytes
                        );

                logo.scaleToFit(
                        92,
                        48
                );

                logo.setAlignment(
                        Element.ALIGN_CENTER
                );

                logoCell.addElement(logo);
            }

            header.addCell(logoCell);

            /*
             * Company name and business description.
             */
            PdfPCell companyCell =
                    new PdfPCell();

            companyCell.setBorder(
                    Rectangle.NO_BORDER
            );

            companyCell.setVerticalAlignment(
                    Element.ALIGN_MIDDLE
            );

            Phrase companyPhrase =
                    new Phrase(
                            "CYOND",
                            COMPANY_FONT
                    );

            companyCell.addElement(
                    new com.lowagie.text.Paragraph(
                            companyPhrase
                    )
            );

            com.lowagie.text.Paragraph tagline =
                    new com.lowagie.text.Paragraph(
                            "Waterproofing Diagnosis & Repair Experts",
                            TAGLINE_FONT
                    );

            tagline.setSpacingBefore(1);
            tagline.setSpacingAfter(4);

            companyCell.addElement(tagline);

            com.lowagie.text.Paragraph title =
                    new com.lowagie.text.Paragraph(
                            "EMPLOYEE PAYSLIP",
                            TITLE_FONT
                    );

            companyCell.addElement(title);

            header.addCell(companyCell);

            /*
             * Pay period.
             */
            PdfPCell periodCell =
                    new PdfPCell();

            periodCell.setBorder(
                    Rectangle.NO_BORDER
            );

            periodCell.setVerticalAlignment(
                    Element.ALIGN_MIDDLE
            );

            com.lowagie.text.Paragraph periodLabel =
                    new com.lowagie.text.Paragraph(
                            "PAY PERIOD",
                            BOLD_FONT
                    );

            periodLabel.setAlignment(
                    Element.ALIGN_RIGHT
            );

            com.lowagie.text.Paragraph periodValue =
                    new com.lowagie.text.Paragraph(
                            payMonth,
                            SECTION_FONT
                    );

            periodValue.setAlignment(
                    Element.ALIGN_RIGHT
            );

            periodCell.addElement(periodLabel);
            periodCell.addElement(periodValue);

            header.addCell(periodCell);

            document.add(header);

            /*
             * Header separator.
             */
            PdfPTable separator =
                    new PdfPTable(1);

            separator.setWidthPercentage(100);

            PdfPCell separatorCell =
                    new PdfPCell(
                            new Phrase("")
                    );

            separatorCell.setBorder(
                    Rectangle.NO_BORDER
            );

            separatorCell.setBackgroundColor(
                    HEADER_BACKGROUND
            );

            separatorCell.setFixedHeight(3);

            separator.addCell(separatorCell);

            document.add(separator);

            /*
             * =====================================================
             * EMPLOYEE INFORMATION
             * =====================================================
             */
            addSectionTitle(
                    document,
                    "EMPLOYEE INFORMATION"
            );

            PdfPTable employeeTable =
                    new PdfPTable(2);

            employeeTable.setWidthPercentage(100);

            employeeTable.setWidths(
                    new float[]{
                            32,
                            68
                    }
            );

            employeeTable.setSpacingAfter(7);

            addInfoRow(
                    employeeTable,
                    "Employee Code",
                    employeeCode
            );

            addInfoRow(
                    employeeTable,
                    "Employee Name",
                    employeeName
            );

            addInfoRow(
                    employeeTable,
                    "Pay Month",
                    payMonth
            );

            addInfoRow(
                    employeeTable,
                    "Payment Status",
                    paymentStatus
            );

            document.add(employeeTable);

            /*
             * =====================================================
             * SALARY DETAILS
             * =====================================================
             */
            addSectionTitle(
                    document,
                    "SALARY DETAILS"
            );

            PdfPTable salaryTable =
                    new PdfPTable(4);

            salaryTable.setWidthPercentage(100);

            salaryTable.setWidths(
                    new float[]{
                            28,
                            22,
                            28,
                            22
                    }
            );

            addTableHeader(
                    salaryTable,
                    "EARNINGS"
            );

            addTableHeaderValue(
                    salaryTable,
                    "AMOUNT"
            );

            addTableHeader(
                    salaryTable,
                    "DEDUCTIONS"
            );

            addTableHeaderValue(
                    salaryTable,
                    "AMOUNT"
            );

            addSalaryPair(
                    salaryTable,
                    "Basic Salary",
                    money(payroll.getBasicSalary()),
                    "Tax",
                    money(payroll.getTaxAmount())
            );

            addSalaryPair(
                    salaryTable,
                    "HRA",
                    money(payroll.getHra()),
                    "PF",
                    money(payroll.getPf())
            );

            addSalaryPair(
                    salaryTable,
                    "Bonus",
                    money(payroll.getBonus()),
                    "Leave Deduction",
                    money(payroll.getLeaveDeduction())
            );

            /*
             * Overtime and Other Deductions intentionally removed.
             */

            addSalaryPair(
                    salaryTable,
                    "Gross Salary",
                    money(payroll.getGrossSalary()),
                    "Total Deductions",
                    money(payroll.getDeductions())
            );

            document.add(salaryTable);

            /*
             * =====================================================
             * NET SALARY
             * =====================================================
             */
            com.lowagie.text.Paragraph spacer =
                    new com.lowagie.text.Paragraph(" ");

            spacer.setLeading(3);

            document.add(spacer);

            PdfPTable netTable =
                    new PdfPTable(2);

            netTable.setWidthPercentage(100);

            netTable.setWidths(
                    new float[]{
                            65,
                            35
                    }
            );

            PdfPCell netLabel =
                    new PdfPCell(
                            new Phrase(
                                    "NET SALARY",
                                    NET_FONT
                            )
                    );

            netLabel.setBackgroundColor(
                    NET_BACKGROUND
            );

            netLabel.setBorderColor(
                    BORDER
            );

            netLabel.setPadding(9);

            PdfPCell netValue =
                    new PdfPCell(
                            new Phrase(
                                    money(payroll.getNetSalary()),
                                    NET_FONT
                            )
                    );

            netValue.setBackgroundColor(
                    NET_BACKGROUND
            );

            netValue.setBorderColor(
                    BORDER
            );

            netValue.setPadding(9);

            netValue.setHorizontalAlignment(
                    Element.ALIGN_RIGHT
            );

            netTable.addCell(netLabel);
            netTable.addCell(netValue);

            document.add(netTable);

            /*
             * PAYSLIP INFORMATION
             * Dynamic payment status, reference, generation date/time
             * and generated-by information.
             */

            document.add(spacer);

            PdfPTable payslipInfoTable = new PdfPTable(2);
            payslipInfoTable.setWidthPercentage(100);
            payslipInfoTable.setWidths(new float[]{35f, 65f});
            payslipInfoTable.setSpacingBefore(8);
            payslipInfoTable.setSpacingAfter(6);

            PdfPCell infoHeader = new PdfPCell(
                    new Phrase("PAYSLIP INFORMATION", SECTION_FONT)
            );
            infoHeader.setColspan(2);
            infoHeader.setPadding(7);
            infoHeader.setBackgroundColor(HEADER_BACKGROUND);
            infoHeader.setBorderColor(BORDER);
            payslipInfoTable.addCell(infoHeader);

            java.time.ZonedDateTime generatedAt =
                    java.time.ZonedDateTime.now(
                            java.time.ZoneId.of("Asia/Kolkata")
                    );

            String generatedDate =
                    generatedAt.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
                    );

            String generatedTime =
                    generatedAt.format(
                            java.time.format.DateTimeFormatter.ofPattern("hh:mm:ss a")
                    ) + " IST";

            String payslipReference =
                    "CY-PAY-"
                            + payroll.getEmployee().getEmployeeCode()
                            + "-"
                            + generatedAt.format(
                                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                            );

            addInfoRow(
                    payslipInfoTable,
                    "Payment Status",
                    String.valueOf(payroll.getStatus())
            );

            addInfoRow(
                    payslipInfoTable,
                    "Payslip Reference",
                    payslipReference
            );

            addInfoRow(
                    payslipInfoTable,
                    "Generated Date",
                    generatedDate
            );

            addInfoRow(
                    payslipInfoTable,
                    "Generated Time",
                    generatedTime
            );

            addInfoRow(
                    payslipInfoTable,
                    "Generated by",
                    "CYOND HR"
            );

            document.add(payslipInfoTable);

            PdfPTable generatedNoteTable = new PdfPTable(1);
            generatedNoteTable.setWidthPercentage(100);
            generatedNoteTable.setSpacingBefore(3);

            PdfPCell generatedNoteCell = new PdfPCell(
                    new Phrase(
                            "This is a computer-generated payslip and does not require a physical signature.",
                            NORMAL_FONT
                    )
            );

            generatedNoteCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            generatedNoteCell.setPadding(6);
            generatedNoteCell.setBackgroundColor(LIGHT_BACKGROUND);
            generatedNoteCell.setBorderColor(BORDER);

            generatedNoteTable.addCell(generatedNoteCell);
            document.add(generatedNoteTable);


            document.close();

            return outputStream.toByteArray();

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Unable to generate payslip PDF",
                    ex
            );
        }
    }

    private void addSectionTitle(
            Document document,
            String title) {

        PdfPTable section =
                new PdfPTable(1);

        section.setWidthPercentage(100);

        section.setSpacingBefore(3);
        section.setSpacingAfter(4);

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                title,
                                SECTION_FONT
                        )
                );

        cell.setBackgroundColor(
                LIGHT_BACKGROUND
        );

        cell.setBorderColor(
                BORDER
        );

        cell.setPadding(5);

        section.addCell(cell);

        document.add(section);
    }

    private void addInfoRow(
            PdfPTable table,
            String label,
            String value) {

        PdfPCell labelCell =
                new PdfPCell(
                        new Phrase(
                                label,
                                BOLD_FONT
                        )
                );

        labelCell.setBackgroundColor(
                LIGHT_BACKGROUND
        );

        labelCell.setBorderColor(
                BORDER
        );

        labelCell.setPadding(5);

        PdfPCell valueCell =
                new PdfPCell(
                        new Phrase(
                                value,
                                NORMAL_FONT
                        )
                );

        valueCell.setBorderColor(
                BORDER
        );

        valueCell.setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTableHeader(
            PdfPTable table,
            String value) {

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                value,
                                BOLD_FONT
                        )
                );

        cell.setBackgroundColor(
                HEADER_BACKGROUND
        );

        cell.setBorderColor(
                BORDER
        );

        cell.setPadding(5);

        table.addCell(cell);
    }

    private void addTableHeaderValue(
            PdfPTable table,
            String value) {

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                value,
                                BOLD_FONT
                        )
                );

        cell.setBackgroundColor(
                HEADER_BACKGROUND
        );

        cell.setBorderColor(
                BORDER
        );

        cell.setPadding(5);

        cell.setHorizontalAlignment(
                Element.ALIGN_RIGHT
        );

        table.addCell(cell);
    }

    private void addSalaryPair(
            PdfPTable table,
            String earningLabel,
            String earningValue,
            String deductionLabel,
            String deductionValue) {

        PdfPCell earningLabelCell =
                new PdfPCell(
                        new Phrase(
                                earningLabel,
                                NORMAL_FONT
                        )
                );

        earningLabelCell.setBorderColor(
                BORDER
        );

        earningLabelCell.setPadding(5);

        PdfPCell earningValueCell =
                new PdfPCell(
                        new Phrase(
                                earningValue,
                                NORMAL_FONT
                        )
                );

        earningValueCell.setBorderColor(
                BORDER
        );

        earningValueCell.setPadding(5);

        earningValueCell.setHorizontalAlignment(
                Element.ALIGN_RIGHT
        );

        PdfPCell deductionLabelCell =
                new PdfPCell(
                        new Phrase(
                                deductionLabel,
                                NORMAL_FONT
                        )
                );

        deductionLabelCell.setBorderColor(
                BORDER
        );

        deductionLabelCell.setPadding(5);

        PdfPCell deductionValueCell =
                new PdfPCell(
                        new Phrase(
                                deductionValue,
                                NORMAL_FONT
                        )
                );

        deductionValueCell.setBorderColor(
                BORDER
        );

        deductionValueCell.setPadding(5);

        deductionValueCell.setHorizontalAlignment(
                Element.ALIGN_RIGHT
        );

        table.addCell(earningLabelCell);
        table.addCell(earningValueCell);
        table.addCell(deductionLabelCell);
        table.addCell(deductionValueCell);
    }

    private String money(
            BigDecimal value) {

        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return "Rs. "
                + value.setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
