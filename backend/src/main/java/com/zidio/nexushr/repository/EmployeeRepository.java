package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByEmployeeCode(String employeeCode);

    long countByLifecycleStatus(EmployeeLifecycleStatus lifecycleStatus);

    long countByRoleType(RoleType roleType);

    List<Employee> findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus lifecycleStatus);

    List<Employee> findByRoleTypeAndActiveTrue(RoleType roleType);

    @Query("""
        SELECT e.department, COUNT(e)
        FROM Employee e
        GROUP BY e.department
        ORDER BY e.department
    """)
    List<Object[]> countEmployeesByDepartment();

    @Query("""
        SELECT e.roleType, COUNT(e)
        FROM Employee e
        GROUP BY e.roleType
        ORDER BY e.roleType
    """)
    List<Object[]> countEmployeesByRole();
}
