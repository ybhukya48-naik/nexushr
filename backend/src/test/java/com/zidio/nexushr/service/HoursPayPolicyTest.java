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
    void casualAndPaidLeaveAreNotDeducted() {
        int day = HoursPayPolicy.PAID_WORK_MINUTES;
        int expected = 10 * day;
        int worked = expected - 2 * day;
        assertEquals(0, HoursPayPolicy.deductibleShortfall(expected, worked));
        assertEquals(
                day,
                HoursPayPolicy.deductibleShortfall(expected, expected - 3 * day));
    }
}
