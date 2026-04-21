package com.mindbridge.agent.controller;

import com.mindbridge.agent.dto.auth.LoginRequest;
import com.mindbridge.agent.dto.auth.RefreshTokenRequest;
import com.mindbridge.agent.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword())
                .map(resp -> ResponseEntity.ok().body((Object) resp))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body((Object) Map.of("error", ex.getMessage()))));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.getRefreshToken())
                .map(resp -> ResponseEntity.ok().body((Object) resp))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body((Object) Map.of("error", ex.getMessage()))));
    }
}
