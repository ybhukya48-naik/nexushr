package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleHistory;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.repository.EmployeeLifecycleHistoryRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLifecycleServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeLifecycleHistoryRepository historyRepository;

    @InjectMocks
    private EmployeeLifecycleService lifecycleService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setEmployeeCode("EMP001");
        employee.setFullName("Test Employee");
        employee.setEmail("employee@example.com");
        employee.setPhone("9999999999");
        employee.setPassword("encoded-password");
        employee.setDepartment("IT");
        employee.setDesignation("Software Engineer");
        employee.setJoiningDate(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("50000"));
        employee.setActive(false);
        employee.setLifecycleStatus(EmployeeLifecycleStatus.PENDING_ONBOARDING);
    }

    @Test
    void submitOnboarding_changesPendingOnboardingToPendingApproval() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.submitOnboarding(
                1L,
                "hr@example.com",
                "Documents verified"
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.PENDING_APPROVAL);

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getEmployee()).isSameAs(employee);
        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.PENDING_ONBOARDING);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.PENDING_APPROVAL);
        assertThat(history.getAction())
                .isEqualTo("SUBMIT_ONBOARDING");
        assertThat(history.getChangedBy())
                .isEqualTo("hr@example.com");
        assertThat(history.getComments())
                .isEqualTo("Documents verified");
        assertThat(history.getChangedAt()).isNotNull();

        verify(employeeRepository).save(employee);
    }

    @Test
    void approveOnboarding_changesPendingApprovalToActive() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.PENDING_APPROVAL);
        employee.setActive(false);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.approveOnboarding(
                1L,
                "admin@example.com",
                "Approved"
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(result.isActive()).isTrue();

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.PENDING_APPROVAL);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getAction())
                .isEqualTo("APPROVE_ONBOARDING");
    }

    @Test
    void promote_changesDesignationAndSalary() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);
        employee.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.promote(
                1L,
                "hr@example.com",
                "Promotion approved",
                "Senior Software Engineer",
                new BigDecimal("70000")
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(result.getDesignation())
                .isEqualTo("Senior Software Engineer");
        assertThat(result.getBaseSalary())
                .isEqualByComparingTo("70000");

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getAction())
                .isEqualTo("PROMOTION");
        assertThat(history.getChangedBy())
                .isEqualTo("hr@example.com");
        assertThat(history.getComments())
                .isEqualTo("Promotion approved");
    }

    @Test
    void promote_keepsExistingSalary_whenNewSalaryIsNotProvided() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        lifecycleService.promote(
                1L,
                "hr@example.com",
                null,
                "Senior Engineer",
                null
        );

        assertThat(employee.getDesignation())
                .isEqualTo("Senior Engineer");
        assertThat(employee.getBaseSalary())
                .isEqualByComparingTo("50000");
    }

    @Test
    void promote_rejectsBlankDesignation() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.promote(
                        1L,
                        "hr@example.com",
                        null,
                        "   ",
                        new BigDecimal("70000")
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New designation is required for promotion");

        verify(historyRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void promote_rejectsNonPositiveSalary() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.promote(
                        1L,
                        "hr@example.com",
                        null,
                        "Senior Engineer",
                        BigDecimal.ZERO
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New base salary must be greater than zero");

        verify(historyRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void transfer_changesDepartment() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.transfer(
                1L,
                "hr@example.com",
                "Business requirement",
                "Finance"
        );

        assertThat(result.getDepartment()).isEqualTo("Finance");
        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getAction())
                .isEqualTo("TRANSFER");
        assertThat(history.getChangedBy())
                .isEqualTo("hr@example.com");
        assertThat(history.getComments())
                .isEqualTo("Business requirement");
    }

    @Test
    void transfer_rejectsBlankDepartment() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.transfer(
                        1L,
                        "hr@example.com",
                        null,
                        " "
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New department is required for transfer");

        verify(historyRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void resign_changesActiveEmployeeToOffboarding() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);
        employee.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.resign(
                1L,
                "employee@example.com",
                "Resignation submitted"
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDING);
        assertThat(result.isActive()).isTrue();

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDING);
        assertThat(history.getAction())
                .isEqualTo("RESIGNATION");
    }

    @Test
    void startOffboarding_changesActiveToOffboarding() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.startOffboarding(
                1L,
                "hr@example.com",
                "Exit process started"
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDING);

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        assertThat(captor.getValue().getAction())
                .isEqualTo("START_OFFBOARDING");
    }

    @Test
    void completeOffboarding_changesOffboardingToOffboardedAndDeactivatesEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDING);
        employee.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = lifecycleService.completeOffboarding(
                1L,
                "hr@example.com",
                "Exit completed"
        );

        assertThat(result.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDED);
        assertThat(result.isActive()).isFalse();

        ArgumentCaptor<EmployeeLifecycleHistory> captor =
                ArgumentCaptor.forClass(EmployeeLifecycleHistory.class);

        verify(historyRepository).save(captor.capture());

        EmployeeLifecycleHistory history = captor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDING);
        assertThat(history.getToStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDED);
        assertThat(history.getAction())
                .isEqualTo("COMPLETE_OFFBOARDING");
    }

    @Test
    void submitOnboarding_rejectsInvalidStatus() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.submitOnboarding(
                        1L,
                        "hr@example.com",
                        null
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid lifecycle transition");

        verify(historyRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void approveOnboarding_rejectsInvalidStatus() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.approveOnboarding(
                        1L,
                        "admin@example.com",
                        null
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid lifecycle transition");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void startOffboarding_rejectsNonActiveEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.PENDING_APPROVAL);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.startOffboarding(
                        1L,
                        "hr@example.com",
                        null
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected status: ACTIVE");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void completeOffboarding_rejectsNonOffboardingEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.completeOffboarding(
                        1L,
                        "hr@example.com",
                        null
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected status: OFFBOARDING");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void promote_rejectsNonActiveEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.PENDING_APPROVAL);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.promote(
                        1L,
                        "hr@example.com",
                        null,
                        "Senior Engineer",
                        new BigDecimal("70000")
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected status: ACTIVE");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void transfer_rejectsNonActiveEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDING);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.transfer(
                        1L,
                        "hr@example.com",
                        null,
                        "Finance"
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected status: ACTIVE");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void resign_rejectsNonActiveEmployee() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDED);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() ->
                lifecycleService.resign(
                        1L,
                        "employee@example.com",
                        null
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected status: ACTIVE");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void getHistory_returnsHistoryOrderedByRepository() {
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        EmployeeLifecycleHistory history1 = new EmployeeLifecycleHistory();
        history1.setEmployee(employee);
        history1.setAction("SUBMIT_ONBOARDING");

        EmployeeLifecycleHistory history2 = new EmployeeLifecycleHistory();
        history2.setEmployee(employee);
        history2.setAction("APPROVE_ONBOARDING");

        List<EmployeeLifecycleHistory> histories =
                List.of(history2, history1);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(historyRepository.findByEmployeeIdOrderByChangedAtDesc(1L))
                .thenReturn(histories);

        List<EmployeeLifecycleHistory> result =
                lifecycleService.getHistory(1L);

        assertThat(result).containsExactlyElementsOf(histories);

        verify(historyRepository)
                .findByEmployeeIdOrderByChangedAtDesc(1L);
    }

    @Test
    void getHistory_rejectsUnknownEmployee() {
        when(employeeRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                lifecycleService.getHistory(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Employee not found with id: 999");

        verify(historyRepository, never())
                .findByEmployeeIdOrderByChangedAtDesc(anyLong());
    }

    @Test
    void submitOnboarding_rejectsUnknownEmployee() {
        when(employeeRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                lifecycleService.submitOnboarding(
                        999L,
                        "hr@example.com",
                        null
                ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Employee not found with id: 999");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void fullLifecycle_canMoveFromOnboardingToOffboarded() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Employee submitted = lifecycleService.submitOnboarding(
                1L,
                "hr@example.com",
                "Submitted"
        );

        assertThat(submitted.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.PENDING_APPROVAL);

        Employee approved = lifecycleService.approveOnboarding(
                1L,
                "admin@example.com",
                "Approved"
        );

        assertThat(approved.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.ACTIVE);
        assertThat(approved.isActive()).isTrue();

        Employee promoted = lifecycleService.promote(
                1L,
                "hr@example.com",
                "Promoted",
                "Senior Engineer",
                new BigDecimal("70000")
        );

        assertThat(promoted.getDesignation())
                .isEqualTo("Senior Engineer");

        Employee transferred = lifecycleService.transfer(
                1L,
                "hr@example.com",
                "Transferred",
                "Engineering"
        );

        assertThat(transferred.getDepartment())
                .isEqualTo("Engineering");

        Employee resigned = lifecycleService.resign(
                1L,
                "employee@example.com",
                "Resigned"
        );

        assertThat(resigned.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDING);

        Employee offboarded = lifecycleService.completeOffboarding(
                1L,
                "hr@example.com",
                "Completed"
        );

        assertThat(offboarded.getLifecycleStatus())
                .isEqualTo(EmployeeLifecycleStatus.OFFBOARDED);
        assertThat(offboarded.isActive()).isFalse();

        verify(historyRepository, times(6)).save(any(EmployeeLifecycleHistory.class));
        verify(employeeRepository, times(6)).save(employee);
    }
}
