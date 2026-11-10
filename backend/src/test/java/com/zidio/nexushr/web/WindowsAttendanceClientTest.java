package com.zidio.nexushr.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsAttendanceClientTest {

    @Test
    void windowsComputerIsAllowed() {
        assertThat(WindowsAttendanceClient.allowed(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0"))
                .isTrue();
    }

    @Test
    void phoneIsRefused() {
        assertThat(WindowsAttendanceClient.allowed(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile"))
                .isFalse();
        assertThat(WindowsAttendanceClient.allowed(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile"))
                .isFalse();
    }

    @Test
    void macComputerIsAllowed() {
        assertThat(WindowsAttendanceClient.allowed(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0"))
                .isTrue();
    }
}
