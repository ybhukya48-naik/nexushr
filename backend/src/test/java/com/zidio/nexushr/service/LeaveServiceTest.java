package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LeaveService leaveService;

    private Employee employee;
    private LeaveRequest leaveRequest;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setEmployeeCode("E1001");
        employee.setFullName("Aarav Sharma");
        employee.setEmail("aarav.sharma@zidio.com");

        leaveRequest = new LeaveRequest();
        leaveRequest.setId(1L);
        leaveRequest.setEmployee(employee);
        leaveRequest.setStartDate(LocalDate.of(2026, 8, 10));
        leaveRequest.setEndDate(LocalDate.of(2026, 8, 12));
        leaveRequest.setReason("Annual vacation");
        leaveRequest.setStatus(LeaveStatus.PENDING);
    }

    @Test
    void create_savesAndReturnsLeaveRequest() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.existsOverlappingLeave(
                eq(1L),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                anyCollection()
        )).thenReturn(false);

        when(leaveRequestRepository.save(leaveRequest))
                .thenReturn(leaveRequest);

        LeaveRequest result = leaveService.create(leaveRequest);

        assertThat(result).isSameAs(leaveRequest);
        assertThat(result.getStatus()).isEqualTo(LeaveStatus.PENDING);

        verify(employeeRepository).findById(1L);
        verify(leaveRequestRepository).existsOverlappingLeave(
                eq(1L),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                anyCollection()
        );
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void create_forcesStatusToPending() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.existsOverlappingLeave(
                eq(1L),
                any(LocalDate.class),
                any(LocalDate.class),
                anyCollection()
        )).thenReturn(false);

        when(leaveRequestRepository.save(leaveRequest))
                .thenReturn(leaveRequest);

        LeaveRequest result = leaveService.create(leaveRequest);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void create_rejectsNullRequest() {
        assertThatThrownBy(() -> leaveService.create(null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Leave request is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsMissingEmployee() {
        leaveRequest.setEmployee(null);

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Employee ID is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsMissingStartDate() {
        leaveRequest.setStartDate(null);

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Start date is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsMissingEndDate() {
        leaveRequest.setEndDate(null);

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("End date is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsStartDateAfterEndDate() {
        leaveRequest.setStartDate(LocalDate.of(2026, 8, 20));
        leaveRequest.setEndDate(LocalDate.of(2026, 8, 10));

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Start date cannot be after end date");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsBlankReason() {
        leaveRequest.setReason("   ");

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Leave reason is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void create_rejectsUnknownEmployee() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Employee not found: 1");

        verify(employeeRepository).findById(1L);
        verifyNoInteractions(leaveRequestRepository);
    }

    @Test
    void create_rejectsOverlappingLeave() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.existsOverlappingLeave(
                eq(1L),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                anyCollection()
        )).thenReturn(true);

        assertThatThrownBy(() -> leaveService.create(leaveRequest))
                .isInstanceOf(Exception.class)
                .hasMessageContaining(
                        "Leave request overlaps an existing pending or approved leave"
                );

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void findAll_returnsList() {
        when(leaveRequestRepository.findAll())
                .thenReturn(List.of(leaveRequest));

        List<LeaveRequest> result = leaveService.findAll();

        assertThat(result).containsExactly(leaveRequest);
    }

    @Test
    void findAll_returnsEmptyList_whenNoRequests() {
        when(leaveRequestRepository.findAll())
                .thenReturn(List.of());

        assertThat(leaveService.findAll()).isEmpty();
    }

    @Test
    void updateStatus_approvesRequest() {
        when(leaveRequestRepository.findById(1L))
                .thenReturn(Optional.of(leaveRequest));

        when(leaveRequestRepository.save(leaveRequest))
                .thenReturn(leaveRequest);

        LeaveRequest result =
                leaveService.updateStatus(1L, LeaveStatus.APPROVED);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void updateStatus_rejectsRequest() {
        when(leaveRequestRepository.findById(1L))
                .thenReturn(Optional.of(leaveRequest));

        when(leaveRequestRepository.save(leaveRequest))
                .thenReturn(leaveRequest);

        LeaveRequest result =
                leaveService.updateStatus(1L, LeaveStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void updateStatus_rejectsAlreadyApprovedRequest() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findById(1L))
                .thenReturn(Optional.of(leaveRequest));

        assertThatThrownBy(() ->
                leaveService.updateStatus(1L, LeaveStatus.REJECTED))
                .isInstanceOf(Exception.class)
                .hasMessageContaining(
                        "Only pending leave requests can be approved or rejected"
                );

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void updateStatus_rejectsAlreadyRejectedRequest() {
        leaveRequest.setStatus(LeaveStatus.REJECTED);

        when(leaveRequestRepository.findById(1L))
                .thenReturn(Optional.of(leaveRequest));

        assertThatThrownBy(() ->
                leaveService.updateStatus(1L, LeaveStatus.APPROVED))
                .isInstanceOf(Exception.class)
                .hasMessageContaining(
                        "Only pending leave requests can be approved or rejected"
                );

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void updateStatus_rejectsNullId() {
        assertThatThrownBy(() ->
                leaveService.updateStatus(null, LeaveStatus.APPROVED))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Leave request ID is required");

        verifyNoInteractions(leaveRequestRepository);
    }

    @Test
    void updateStatus_rejectsNullStatus() {
        assertThatThrownBy(() ->
                leaveService.updateStatus(1L, null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Leave status is required");

        verifyNoInteractions(leaveRequestRepository);
    }

    @Test
    void updateStatus_throwsException_whenNotFound() {
        when(leaveRequestRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                leaveService.updateStatus(99L, LeaveStatus.APPROVED))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Leave request not found: 99");
    }

    @Test
    void getLeaveBalance_calculatesApprovedLeaveDays() {
        LeaveRequest approvedLeaveOne = new LeaveRequest();
        approvedLeaveOne.setEmployee(employee);
        approvedLeaveOne.setStartDate(LocalDate.of(2026, 8, 10));
        approvedLeaveOne.setEndDate(LocalDate.of(2026, 8, 12));
        approvedLeaveOne.setStatus(LeaveStatus.APPROVED);

        LeaveRequest approvedLeaveTwo = new LeaveRequest();
        approvedLeaveTwo.setEmployee(employee);
        approvedLeaveTwo.setStartDate(LocalDate.of(2026, 9, 1));
        approvedLeaveTwo.setEndDate(LocalDate.of(2026, 9, 2));
        approvedLeaveTwo.setStatus(LeaveStatus.APPROVED);

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L,
                LeaveStatus.APPROVED
        )).thenReturn(List.of(
                approvedLeaveOne,
                approvedLeaveTwo
        ));

        org.springframework.test.util.ReflectionTestUtils.setField(
                leaveService,
                "annualEntitlementDays",
                24
        );

        var result = leaveService.getLeaveBalance(1L);

        assertThat(result.getEmployeeId()).isEqualTo(1L);
        assertThat(result.getAnnualEntitlement()).isEqualTo(24);
        assertThat(result.getUsedDays()).isEqualTo(5);
        assertThat(result.getRemainingDays()).isEqualTo(19);

        verify(employeeRepository).findById(1L);
        verify(leaveRequestRepository)
                .findByEmployee_IdAndStatus(1L, LeaveStatus.APPROVED);
    }

    @Test
    void getLeaveBalance_ignoresPendingAndRejectedLeave() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaveService,
                "annualEntitlementDays",
                24
        );

        LeaveRequest approved = new LeaveRequest();
        approved.setEmployee(employee);
        approved.setStartDate(LocalDate.of(2026, 8, 10));
        approved.setEndDate(LocalDate.of(2026, 8, 12));
        approved.setStatus(LeaveStatus.APPROVED);

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L,
                LeaveStatus.APPROVED
        )).thenReturn(List.of(approved));

        var result = leaveService.getLeaveBalance(1L);

        assertThat(result.getUsedDays()).isEqualTo(3);
        assertThat(result.getRemainingDays()).isEqualTo(21);
    }

    @Test
    void getLeaveBalance_returnsFullEntitlement_whenNoApprovedLeave() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                leaveService,
                "annualEntitlementDays",
                24
        );

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L,
                LeaveStatus.APPROVED
        )).thenReturn(List.of());

        var result = leaveService.getLeaveBalance(1L);

        assertThat(result.getAnnualEntitlement()).isEqualTo(24);
        assertThat(result.getUsedDays()).isZero();
        assertThat(result.getRemainingDays()).isEqualTo(24);
    }

    @Test
    void getLeaveBalance_neverReturnsNegativeRemainingDays() {
        LeaveRequest approvedLeave = new LeaveRequest();
        approvedLeave.setEmployee(employee);
        approvedLeave.setStartDate(LocalDate.of(2026, 1, 1));
        approvedLeave.setEndDate(LocalDate.of(2026, 2, 15));
        approvedLeave.setStatus(LeaveStatus.APPROVED);

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L,
                LeaveStatus.APPROVED
        )).thenReturn(List.of(approvedLeave));

        var result = leaveService.getLeaveBalance(1L);

        assertThat(result.getUsedDays()).isEqualTo(46);
        assertThat(result.getRemainingDays()).isZero();
    }

    @Test
    void getLeaveBalance_rejectsNullEmployeeId() {
        assertThatThrownBy(() ->
                leaveService.getLeaveBalance(null))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Employee ID is required");

        verifyNoInteractions(employeeRepository, leaveRequestRepository);
    }

    @Test
    void getLeaveBalance_rejectsUnknownEmployee() {
        when(employeeRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                leaveService.getLeaveBalance(99L))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Employee not found: 99");

        verify(employeeRepository).findById(99L);
        verifyNoInteractions(leaveRequestRepository);
    }
}
