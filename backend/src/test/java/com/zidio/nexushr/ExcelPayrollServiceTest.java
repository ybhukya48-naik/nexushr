package com.zidio.nexushr;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.service.ExcelPayrollService;
import com.zidio.nexushr.service.email.ResendEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExcelPayrollServiceTest {

    @MockBean
    private ResendEmailService resendEmailService;

    @Autowired
    private com.zidio.nexushr.repository.EmployeeRepository employeeRepository;

    @Autowired
    private ExcelPayrollService excelPayrollService;

    @Test
    void importActualPayrollWorkbook() throws Exception {

        String filePath = "GGEA-CYOND-SALARIES-2026.xlsx";

        try (FileInputStream inputStream =
                     new FileInputStream(filePath)) {

            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "GGEA-CYOND-SALARIES-2026.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            inputStream
                    );

            List<PayrollRecord> records =
                    excelPayrollService.importPayroll(
                            file,
                            "2026-09"
                    );

            System.out.println();
            System.out.println("========================================");
            System.out.println("EXCEL PAYROLL IMPORT RESULT");
            System.out.println("========================================");
            System.out.println("Created records: " + records.size());

            for (PayrollRecord record : records) {

                System.out.println("----------------------------------------");

                System.out.println(
                        "Employee: "
                                + record.getEmployee().getEmployeeCode()
                                + " / "
                                + record.getEmployee().getFullName()
                );

                System.out.println(
                        "Company: "
                                + record.getEmployee()
                                        .getCompany()
                                        .getCode()
                );

                System.out.println(
                        "CTC: "
                                + record.getCtc()
                );

                System.out.println(
                        "Working Days: "
                                + record.getWorkingDays()
                );

                System.out.println(
                        "Required Hours: "
                                + record.getRequiredHours()
                );

                System.out.println(
                        "Actual Hours: "
                                + record.getActualHours()
                );

                System.out.println(
                        "Casual Leave: "
                                + record.getCasualLeave()
                );

                System.out.println(
                        "Earned Gross: "
                                + record.getEarnedGross()
                );

                System.out.println(
                        "Basic: "
                                + record.getBasicSalary()
                );

                System.out.println(
                        "HRA: "
                                + record.getHra()
                );

                System.out.println(
                        "Conveyance: "
                                + record.getConveyance()
                );

                System.out.println(
                        "Medical: "
                                + record.getMedical()
                );

                System.out.println(
                        "Others: "
                                + record.getOthers()
                );

                System.out.println(
                        "PF: "
                                + record.getPf()
                );

                System.out.println(
                        "ESI: "
                                + record.getEsi()
                );

                System.out.println(
                        "PT: "
                                + record.getPt()
                );

                System.out.println(
                        "Gross Salary: "
                                + record.getGrossSalary()
                );

                System.out.println(
                        "Net Salary: "
                                + record.getNetSalary()
                );
            }

            System.out.println("========================================");
            System.out.println();

            assertNotNull(records);
        }
    }
}
