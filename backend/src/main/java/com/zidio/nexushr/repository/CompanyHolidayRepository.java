package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {

    List<CompanyHoliday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("""
            select count(holiday) > 0 from CompanyHoliday holiday
            where holiday.holidayDate = :date
              and (
                (:companyCode is null and (holiday.companyCode is null or holiday.companyCode = ''))
                or lower(holiday.companyCode) = lower(:companyCode)
              )
            """)
    boolean existsForDay(@Param("date") LocalDate date, @Param("companyCode") String companyCode);
}
