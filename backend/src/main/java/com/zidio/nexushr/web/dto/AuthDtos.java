package com.zidio.nexushr.web.dto;

public class AuthDtos {

    public record LoginRequest(String username, String password) {}

    /** Access token response — also includes username so the UI can display it. */
    public record LoginResponse(String accessToken, String role, String username) {}

    public record RefreshRequest(String accessToken) {}
}
