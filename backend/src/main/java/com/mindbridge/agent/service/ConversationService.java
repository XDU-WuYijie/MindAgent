package com.mindbridge.agent.service;

import com.mindbridge.agent.config.ChatMemoryProperties;
import com.mindbridge.agent.entity.ChatMessage;
import com.mindbridge.agent.entity.PsychologicalReport;
import com.mindbridge.agent.repository.ChatMessageRepository;
import com.mindbridge.agent.repository.PsychologicalReportRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ConversationService {

    private static final TypeReference<List<MemoryMessage>> MEMORY_TYPE = new TypeReference<>() {
    };
    private static final Pattern KEY_SANITIZER = Pattern.compile("[^a-zA-Z0-9:_-]");

    private final ChatMessageRepository chatMessageRepository;
    private final PsychologicalReportRepository reportRepository;
    private final ChatMemoryProperties memoryProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConversationService(ChatMessageRepository chatMessageRepository,
                               PsychologicalReportRepository reportRepository,
                               ChatMemoryProperties memoryProperties,
                               ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                               ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
        this.memoryProperties = memoryProperties;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public Mono<List<Map<String, Object>>> loadRecentHistory(Long userId, String conversationId) {
        return Mono.fromCallable(() -> {
                    List<Map<String, Object>> redisHistory = loadFromRedis(userId, conversationId);
                    if (!redisHistory.isEmpty()) {
                        return redisHistory;
                    }
                    if (!memoryProperties.isFallbackToDb()) {
                        return List.<Map<String, Object>>of();
                    }
                    List<ChatMessage> rows = chatMessageRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
                    int limit = Math.max(0, memoryProperties.getHistoryLimit());
                    if (rows.size() > limit) {
                        rows = rows.subList(0, limit);
                    }
                    Collections.reverse(rows);
                    List<Map<String, Object>> messages = new ArrayList<>();
                    for (ChatMessage row : rows) {
                        messages.add(ChatMessageFactory.message(row.getRole(), row.getContent()));
                    }
                    return messages;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(List.<Map<String, Object>>of());
    }

    public void saveConversationAsync(Long userId,
                                      String conversationId,
                                      String query,
                                      String answer,
                                      IntentType intent) {
        Mono.fromRunnable(() -> {
                    ChatMessage userMsg = new ChatMessage();
                    userMsg.setUserId(userId);
                    userMsg.setRole("user");
                    userMsg.setContent(query);
                    userMsg.setIntent(intent.name());
                    chatMessageRepository.save(userMsg);

                    ChatMessage assistantMsg = new ChatMessage();
                    assistantMsg.setUserId(userId);
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(answer == null ? "" : answer);
                    assistantMsg.setIntent(intent.name());
                    chatMessageRepository.save(assistantMsg);

                    saveToRedis(userId, conversationId, query, answer);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public void saveReportAsync(Long userId, String query, IntentType intent, int ragContexts) {
        Mono.fromRunnable(() -> {
                    PsychologicalReport report = new PsychologicalReport();
                    report.setUserId(userId);
                    report.setQueryText(query);
                    report.setIntent(intent.name());
                    report.setRiskLevel(toRiskLevel(intent));
                    report.setRagContexts(ragContexts);
                    reportRepository.save(report);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private String toRiskLevel(IntentType intent) {
        if (intent == IntentType.RISK) {
            return "high";
        }
        if (intent == IntentType.CONSULT) {
            return "medium";
        }
        return "low";
    }

    private List<Map<String, Object>> loadFromRedis(Long userId, String conversationId) {
        if (!memoryProperties.isRedisEnabled() || redisTemplate == null) {
            return List.<Map<String, Object>>of();
        }
        try {
            String raw = redisTemplate.opsForValue().get(memoryKey(userId, conversationId));
            if (raw == null || raw.isBlank()) {
                return List.<Map<String, Object>>of();
            }
            List<MemoryMessage> rows = objectMapper.readValue(raw, MEMORY_TYPE);
            int limit = Math.max(0, memoryProperties.getHistoryLimit());
            if (limit > 0 && rows.size() > limit) {
                rows = rows.subList(rows.size() - limit, rows.size());
            }
            List<Map<String, Object>> messages = new ArrayList<>(rows.size());
            for (MemoryMessage row : rows) {
                if (row.role() == null || row.content() == null) {
                    continue;
                }
                messages.add(ChatMessageFactory.message(row.role(), row.content()));
            }
            return messages;
        } catch (Exception ignored) {
            return List.<Map<String, Object>>of();
        }
    }

    private void saveToRedis(Long userId, String conversationId, String query, String answer) {
        if (!memoryProperties.isRedisEnabled() || redisTemplate == null) {
            return;
        }
        try {
            String key = memoryKey(userId, conversationId);
            List<MemoryMessage> rows;
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                rows = new ArrayList<>();
            } else {
                rows = objectMapper.readValue(raw, MEMORY_TYPE);
            }
            rows.add(new MemoryMessage("user", query == null ? "" : query));
            rows.add(new MemoryMessage("assistant", answer == null ? "" : answer));

            int limit = Math.max(0, memoryProperties.getHistoryLimit());
            if (limit > 0 && rows.size() > limit) {
                rows = new ArrayList<>(rows.subList(rows.size() - limit, rows.size()));
            }

            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows));
            long ttlMinutes = memoryProperties.getTtlMinutes();
            if (ttlMinutes > 0) {
                redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
            }
        } catch (Exception ignored) {
            // Keep DB as source-of-truth fallback.
        }
    }

    private String memoryKey(Long userId, String conversationId) {
        String normalizedConversation = normalizeConversationId(conversationId);
        return memoryProperties.getKeyPrefix() + ":" + userId + ":" + normalizedConversation;
    }

    private String normalizeConversationId(String conversationId) {
        String fallback = memoryProperties.getDefaultConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return fallback;
        }
        String trimmed = conversationId.trim();
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        String safe = KEY_SANITIZER.matcher(trimmed).replaceAll("_");
        return safe.isBlank() ? fallback : safe;
    }

    private record MemoryMessage(String role, String content) {
    }
}
