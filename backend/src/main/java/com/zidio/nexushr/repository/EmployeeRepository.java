package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT email, password, employee_code, role_type, active, lifecycle_status, full_name
            FROM employees
            WHERE lower(employee_code) = lower(:login)
               OR lower(email) = lower(:login)
            """, nativeQuery = true)
    List<Object[]> findLoginMatches(@Param("login") String login);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE employees
            SET password = :password
            WHERE lower(email) = lower(:email)
              AND COALESCE(active, true) = true
              AND (role_type IS NULL OR role_type NOT IN ('ADMIN', 'HR', 'MANAGER'))
            """, nativeQuery = true)
    int updateEmployeePassword(@Param("email") String email, @Param("password") String password);

    @Query(value = """
            SELECT role_type
            FROM employees
            WHERE lower(email) = lower(:email)
            """, nativeQuery = true)
    List<String> findRoleTypesByEmail(@Param("email") String email);

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
    Optional<Employee> findByFullNameIgnoreCase(String fullName);
}

