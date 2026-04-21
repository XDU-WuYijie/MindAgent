package com.mindbridge.agent.dto.auth;

public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String token;
    private Long userId;
    private String username;
    private String role;

    public LoginResponse(String accessToken,
                         String refreshToken,
                         Long userId,
                         String username,
                         String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.token = accessToken;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public LoginResponse(String token, Long userId, String username, String role) {
        this(token, null, userId, username, role);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
