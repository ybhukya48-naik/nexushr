package com.zidio.nexushr.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CyondBrowserGateFilterTest {

    @Test
    void chromeAddressBarCannotOpenTheApi() {
        assertThat(CyondBrowserGateFilter.permitted(
                null, "none", "navigate", "/api/v1/employees")).isFalse();
        assertThat(CyondBrowserGateFilter.permitted(
                null, "none", "navigate", "/api/v1/payroll")).isFalse();
    }

    @Test
    void otherWebsitesCannotReadEmployeeOrPayrollData() {
        assertThat(CyondBrowserGateFilter.permitted(
                "https://evil.example", "cross-site", "cors", "/api/v1/employees")).isFalse();
        assertThat(CyondBrowserGateFilter.permitted(
                "https://hr-api.cyond.com", "same-origin", "cors", "/api/v1/payroll")).isFalse();
    }

    @Test
    void cyondSiteCanCallTheProxy() {
        assertThat(CyondBrowserGateFilter.permitted(
                "https://hr.cyond.com", "same-origin", "cors", "/api/v1/employees")).isTrue();
        assertThat(CyondBrowserGateFilter.permitted(
                "https://cyond.com", "cross-site", "cors", "/api/v1/payroll")).isTrue();
        assertThat(CyondBrowserGateFilter.permitted(
                "https://www.cyond.com", "same-origin", "cors", "/api/v1/employees/me")).isTrue();
    }

    @Test
    void healthCheckStaysOpenForTheHost() {
        assertThat(CyondBrowserGateFilter.permitted(
                null, null, null, "/actuator/health")).isTrue();
    }
}
