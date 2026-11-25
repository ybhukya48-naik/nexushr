package com.zidio.nexushr;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Base class for integration tests.
 *
 * Uses a real PostgreSQL database through Testcontainers.
 * Flyway migrations are executed against PostgreSQL.
 *
 * Redis is excluded because it is not required for these
 * data/API integration tests.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Redis is not required for integration tests
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",

                // Use Flyway migrations
                "spring.flyway.enabled=true",

                // Hibernate validates the schema created by Flyway
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
public abstract class AbstractIntegrationTest {

    @Autowired
    private EmployeeRepository testEmployeeRepository;

    @Autowired
    private EmployeeService testEmployeeService;

    protected Employee testEmployee(String code, String email, RoleType role, String password) {
        return testEmployeeRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            EmployeeRequest request = new EmployeeRequest();
            request.setEmployeeCode(code);
            request.setFullName("Integration " + code);
            request.setEmail(email);
            request.setPassword(password);
            request.setRoleType(role);
            request.setDepartment("Integration");
            request.setDesignation("Tester");
            request.setJoiningDate(LocalDate.of(2026, 1, 1));
            request.setBaseSalary(BigDecimal.valueOf(50000));
            request.setCompanyCode("CYOND");
            return testEmployeeService.create(request);
        });
    }

    /**
     * One PostgreSQL database for every integration test class.
     * Started here, not with {@code @Container}, so Testcontainers does not
     * stop it when a test class finishes and leave Spring pointing at the old port.
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("nexushr_it")
                    .withUsername("nexushr")
                    .withPassword("nexushr");

    static {
        POSTGRES.start();
    }

    /**
     * Random HTTP port assigned by Spring Boot.
     */
    @LocalServerPort
    protected int port;

    /**
     * Override the normal H2 test datasource with the
     * PostgreSQL Testcontainers datasource.
     */
    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        // IMPORTANT:
        // src/test/resources/application.properties sets
        // org.h2.Driver. Override it for this PostgreSQL
        // integration test.
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
    }

    /**
     * Base URL for REST API integration tests.
     */
    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}
