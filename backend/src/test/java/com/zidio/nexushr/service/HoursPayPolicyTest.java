package com.zidio.nexushr.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoursPayPolicyTest {

    @Test
    void gorleSecondSaturdayIsNotARequiredDay() {
        LocalDate october = LocalDate.of(2026, 10, 10);
        assertTrue(HoursPayPolicy.isSecondSaturday(october));

        int cyond = HoursPayPolicy.expectedMinutes(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                false);
        int gorle = HoursPayPolicy.expectedMinutes(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true);

        assertEquals(HoursPayPolicy.DAY_MINUTES, cyond - gorle);
    }

    @Test
    void oneCasualLeaveIsNotDeducted() {
        int expected = 10 * HoursPayPolicy.DAY_MINUTES;
        int worked = expected - HoursPayPolicy.DAY_MINUTES;
        assertEquals(0, HoursPayPolicy.deductibleShortfall(expected, worked));
        assertEquals(
                2 * HoursPayPolicy.DAY_MINUTES,
                HoursPayPolicy.deductibleShortfall(expected, expected - 3 * HoursPayPolicy.DAY_MINUTES));
    }
}
