package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByCode(String code);
    Optional<Company> findByCodeIgnoreCase(String code);
}
