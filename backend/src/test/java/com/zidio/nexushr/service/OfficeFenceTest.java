package com.zidio.nexushr.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeFenceTest {

    @Test
    void officePointIsInside() {
        assertThat(OfficeFence.inside(OfficeFence.OFFICE_LATITUDE, OfficeFence.OFFICE_LONGITUDE)).isTrue();
    }

    @Test
    void distantPointIsOutside() {
        assertThat(OfficeFence.inside(17.45, 78.50)).isFalse();
    }

    @Test
    void missingPointIsOutside() {
        assertThat(OfficeFence.inside(null, null)).isFalse();
    }
}
