package com.zidio.nexushr.web.dto;

import java.time.LocalDate;

public record DailyCheckInCount(LocalDate date, long checkedIn) {
}
