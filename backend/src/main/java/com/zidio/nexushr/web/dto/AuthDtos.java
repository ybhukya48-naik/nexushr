package com.zidio.nexushr.web.dto;

public class AuthDtos {

    public record LoginRequest(String username, String password) {}

    public record CreatePasswordRequest(String email, String password) {}

    public record SetPasswordRequest(String email, String currentPassword, String newPassword) {}

    public record ResetPasswordRequest(String email) {}

    /** Access token response — also includes username so the UI can display it. */
    public record LoginResponse(
            String accessToken,
            String role,
            String username,
            boolean mustChangePassword,
            boolean profileCompleted
    ) {}

    public record RefreshRequest(String accessToken) {}
}
