package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.repository.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendanceRecord record;
    private final LocalDate date = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        record = new AttendanceRecord();
        record.setId(1L);
        record.setAttendanceDate(date);
        record.setWorkMinutes(480);
    }

    @Test
    void create_savesAndReturnsRecord() {
        when(attendanceRepository.save(record)).thenReturn(record);

        AttendanceRecord result = attendanceService.create(record);

        assertThat(result).isSameAs(record);
        verify(attendanceRepository).save(record);
    }

    @Test
    void listByDate_returnsRecordsForDate() {
        when(attendanceRepository.findByAttendanceDate(date)).thenReturn(List.of(record));

        List<AttendanceRecord> result = attendanceService.listByDate(date);

        assertThat(result).containsExactly(record);
        verify(attendanceRepository).findByAttendanceDate(date);
    }

    @Test
    void listByDate_returnsEmptyList_whenNoRecordsForDate() {
        LocalDate noRecordsDate = LocalDate.of(2025, 1, 1);
        when(attendanceRepository.findByAttendanceDate(noRecordsDate)).thenReturn(List.of());

        assertThat(attendanceService.listByDate(noRecordsDate)).isEmpty();
    }
}
