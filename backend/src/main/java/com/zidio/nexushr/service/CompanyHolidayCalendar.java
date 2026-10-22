package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.CompanyHoliday;
import com.zidio.nexushr.repository.CompanyHolidayRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompanyHolidayCalendar {

    private final CompanyHolidayRepository companyHolidayRepository;

    public CompanyHolidayCalendar(CompanyHolidayRepository companyHolidayRepository) {
        this.companyHolidayRepository = companyHolidayRepository;
    }

    public List<CompanyHoliday> between(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return List.of();
        }
        return companyHolidayRepository.findByHolidayDateBetween(start, end);
    }

    public Set<LocalDate> dates(LocalDate start, LocalDate end, String companyCode) {
        return forCompany(between(start, end), companyCode);
    }

    public Set<LocalDate> forCompany(List<CompanyHoliday> holidays, String companyCode) {
        if (holidays == null || holidays.isEmpty()) {
            return Set.of();
        }
        return holidays.stream()
                .filter(holiday -> applies(holiday, companyCode))
                .map(CompanyHoliday::getHolidayDate)
                .collect(Collectors.toSet());
    }

    private static boolean applies(CompanyHoliday holiday, String companyCode) {
        String code = holiday.getCompanyCode();
        return code == null || code.isBlank() || code.equalsIgnoreCase(companyCode);
    }
}
