package com.mindagent.agent.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUserSupport {

    public Long requireUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        Object claim = jwt.getClaim("uid");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token uid");
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token uid");
    }

    public String requireRole(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        Object claim = jwt.getClaim("roles");
        if (claim instanceof Iterable<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    return String.valueOf(role);
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token roles");
    }
}
