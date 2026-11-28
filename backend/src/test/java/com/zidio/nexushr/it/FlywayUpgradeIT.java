package com.zidio.nexushr.it;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayUpgradeIT {

    @Test
    void upgradesPopulatedV42DatabaseWithoutChangingExistingValues() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .target("42")
                    .load()
                    .migrate();

            long employeeId;
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                try (ResultSet employees = statement.executeQuery("SELECT id FROM employees ORDER BY id LIMIT 1")) {
                    assertThat(employees.next()).isTrue();
                    employeeId = employees.getLong(1);
                }
                statement.executeUpdate("UPDATE employees SET base_salary = 24000 WHERE id = " + employeeId);
                statement.executeUpdate("INSERT INTO attendance_records (employee_id, attendance_date, work_minutes) "
                        + "VALUES (" + employeeId + ", DATE '2099-01-01', 480)");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT e.base_salary, e.annual_ctc, a.work_minutes, a.manual_lunch, a.lunch_started_at "
                                 + "FROM employees e JOIN attendance_records a ON a.employee_id = e.id "
                                 + "WHERE e.id = " + employeeId + " AND a.attendance_date = DATE '2099-01-01'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBigDecimal("base_salary")).isEqualByComparingTo("24000");
                assertThat(row.getBigDecimal("annual_ctc")).isNull();
                assertThat(row.getInt("work_minutes")).isEqualTo(480);
                assertThat(row.getBoolean("manual_lunch")).isFalse();
                assertThat(row.getTimestamp("lunch_started_at")).isNull();
            }
        }
    }
}
