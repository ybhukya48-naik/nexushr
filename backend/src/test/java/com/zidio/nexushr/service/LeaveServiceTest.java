package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private LeaveService leaveService;

    private LeaveRequest leaveRequest;

    @BeforeEach
    void setUp() {
        leaveRequest = new LeaveRequest();
        leaveRequest.setId(1L);
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setReason("Annual vacation");
    }

    @Test
    void create_savesAndReturnsLeaveRequest() {
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        LeaveRequest result = leaveService.create(leaveRequest);

        assertThat(result).isSameAs(leaveRequest);
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void findAll_returnsList() {
        when(leaveRequestRepository.findAll()).thenReturn(List.of(leaveRequest));

        List<LeaveRequest> result = leaveService.findAll();

        assertThat(result).containsExactly(leaveRequest);
    }

    @Test
    void findAll_returnsEmptyList_whenNoRequests() {
        when(leaveRequestRepository.findAll()).thenReturn(List.of());

        assertThat(leaveService.findAll()).isEmpty();
    }

    @Test
    void updateStatus_approvesRequest() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        LeaveRequest result = leaveService.updateStatus(1L, LeaveStatus.APPROVED);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        verify(leaveRequestRepository).save(leaveRequest);
    }

    @Test
    void updateStatus_rejectsRequest() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(leaveRequest)).thenReturn(leaveRequest);

        LeaveRequest result = leaveService.updateStatus(1L, LeaveStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void updateStatus_throwsException_whenNotFound() {
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.updateStatus(99L, LeaveStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave request not found: 99");
    }
}
