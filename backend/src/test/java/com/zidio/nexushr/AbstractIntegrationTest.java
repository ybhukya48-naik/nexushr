package com.zidio.nexushr;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    /**
     * PostgreSQL database used by integration tests.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("nexushr_it")
                    .withUsername("nexushr")
                    .withPassword("nexushr");

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
