package com.mindbridge.agent.service;

import com.mindbridge.agent.config.McpProperties;
import com.mindbridge.agent.entity.McpDispatchLog;
import com.mindbridge.agent.repository.McpDispatchLogRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class McpDispatchService {

    private final WebClient.Builder webClientBuilder;
    private final McpProperties mcpProperties;
    private final McpDispatchLogRepository logRepository;

    public McpDispatchService(WebClient.Builder webClientBuilder,
                              McpProperties mcpProperties,
                              McpDispatchLogRepository logRepository) {
        this.webClientBuilder = webClientBuilder;
        this.mcpProperties = mcpProperties;
        this.logRepository = logRepository;
    }

    public void dispatchAsync(Long userId,
                              String query,
                              IntentType intent,
                              String riskLevel,
                              int ragContexts) {
        if (!mcpProperties.isEnabled() || intent == IntentType.CHAT) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("intent", intent.name());
        payload.put("riskLevel", riskLevel);
        payload.put("ragContexts", ragContexts);
        payload.put("query", query);

        Mono<Void> excelDispatch = dispatchWithRetry(
                mcpProperties.getExcel().isEnabled(),
                mcpProperties.getExcel().getUrl(),
                "excel_write",
                userId,
                payload
        );

        Mono<Void> emailDispatch = (intent == IntentType.RISK)
                ? dispatchWithRetry(
                    mcpProperties.getEmail().isEnabled(),
                    mcpProperties.getEmail().getUrl(),
                    "email_alert",
                    userId,
                    payload
                )
                : Mono.empty();

        excelDispatch.then(emailDispatch).subscribe();
    }

    private Mono<Void> dispatchWithRetry(boolean enabled,
                                         String url,
                                         String action,
                                         Long userId,
                                         Map<String, Object> payload) {
        if (!enabled || url == null || url.isBlank()) {
            return Mono.empty();
        }
        int maxAttempts = Math.max(1, mcpProperties.getRetry().getMaxAttempts());
        long delay = Math.max(50L, mcpProperties.getRetry().getDelayMs());

        return webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .then()
                .retryWhen(Retry.fixedDelay(maxAttempts - 1, Duration.ofMillis(delay)))
                .doOnSuccess(unused -> saveDispatchLog(userId, action, "SUCCESS", maxAttempts, null))
                .onErrorResume(ex -> {
                    saveDispatchLog(userId, action, "FAILED", maxAttempts, ex.getMessage());
                    return Mono.empty();
                });
    }

    private void saveDispatchLog(Long userId,
                                 String action,
                                 String status,
                                 int attempts,
                                 String errorMessage) {
        Mono.fromRunnable(() -> {
                    McpDispatchLog log = new McpDispatchLog();
                    log.setUserId(userId);
                    log.setAction(action);
                    log.setStatus(status);
                    log.setAttempts(attempts);
                    log.setErrorMessage(errorMessage);
                    logRepository.save(log);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
