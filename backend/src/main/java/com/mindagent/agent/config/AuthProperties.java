package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.auth")
public class AuthProperties {

    private String jwtSecret = "change-this-to-a-long-random-secret-at-least-32-bytes";
    private String issuer = "mindagent-agent";
    private long expiresMinutes = 120;
    private long refreshExpiresDays = 7;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpiresMinutes() {
        return expiresMinutes;
    }

    public void setExpiresMinutes(long expiresMinutes) {
        this.expiresMinutes = expiresMinutes;
    }

    public long getRefreshExpiresDays() {
        return refreshExpiresDays;
    }

    public void setRefreshExpiresDays(long refreshExpiresDays) {
        this.refreshExpiresDays = refreshExpiresDays;
    }
}
