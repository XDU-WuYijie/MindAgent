package com.mindagent.agent.security;

import com.mindagent.agent.config.AuthProperties;
import com.mindagent.agent.entity.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenService {

    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(authProperties.getExpiresMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("roles", List.of(user.getRole()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String issueToken(AppUser user) {
        return issueAccessToken(user);
    }
}
