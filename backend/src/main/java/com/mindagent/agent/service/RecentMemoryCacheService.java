package com.mindagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.config.ChatMemoryProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecentMemoryCacheService {

    private static final TypeReference<List<RecentMemoryMessage>> RECENT_TYPE = new TypeReference<>() {
    };

    private final ChatMemoryProperties properties;
    private final ChatMessageService chatMessageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RecentMemoryCacheService(ChatMemoryProperties properties,
                                    ChatMessageService chatMessageService,
                                    ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.chatMessageService = chatMessageService;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public List<RecentMemoryMessage> loadRecentWindow(Long userId, Long sessionId, int tokenBudget) {
        return loadRecentWindowSnapshot(userId, sessionId, tokenBudget).messages();
    }

    public RecentWindowSnapshot loadRecentWindowSnapshot(Long userId, Long sessionId, int tokenBudget) {
        List<RecentMemoryMessage> cached = loadFromRedis(userId, sessionId);
        if (!cached.isEmpty()) {
            return new RecentWindowSnapshot("REDIS", trimToBudget(cached, tokenBudget));
        }
        List<RecentMemoryMessage> rebuilt = chatMessageService.loadRecentWindowFromDb(
                userId,
                sessionId,
                tokenBudget,
                properties.getRecentKeepTurns()
        );
        if (!rebuilt.isEmpty()) {
            saveWindow(userId, sessionId, rebuilt);
        }
        return new RecentWindowSnapshot("MYSQL", rebuilt);
    }

    public void refreshAfterAppend(Long userId, Long sessionId, List<RecentMemoryMessage> appendedMessages) {
        List<RecentMemoryMessage> base = loadFromRedis(userId, sessionId);
        if (base.isEmpty()) {
            rebuildFromDb(userId, sessionId);
            return;
        }
        List<RecentMemoryMessage> merged = new ArrayList<>(base);
        merged.addAll(appendedMessages);
        merged = trimToBudget(merged, properties.getInputBudget());
        int maxMessages = Math.max(1, properties.getRecentKeepTurns() * 2);
        if (merged.size() > maxMessages) {
            merged = new ArrayList<>(merged.subList(merged.size() - maxMessages, merged.size()));
        }
        saveWindow(userId, sessionId, merged);
    }

    public void rebuildFromDb(Long userId, Long sessionId) {
        List<RecentMemoryMessage> rebuilt = chatMessageService.loadRecentWindowFromDb(
                userId,
                sessionId,
                properties.getInputBudget(),
                properties.getRecentKeepTurns()
        );
        saveWindow(userId, sessionId, rebuilt);
    }

    public void deleteRecentWindow(Long userId, Long sessionId) {
        if (!isRedisAvailable()) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(userId, sessionId));
        } catch (Exception ignored) {
            // Keep MySQL as source of truth.
        }
    }

    private List<RecentMemoryMessage> loadFromRedis(Long userId, Long sessionId) {
        if (!isRedisAvailable()) {
            return List.of();
        }
        try {
            String raw = redisTemplate.opsForValue().get(buildKey(userId, sessionId));
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(raw, RECENT_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void saveWindow(Long userId, Long sessionId, List<RecentMemoryMessage> rows) {
        if (!isRedisAvailable()) {
            return;
        }
        try {
            String key = buildKey(userId, sessionId);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows));
            if (properties.getRecentTtlMinutes() > 0) {
                redisTemplate.expire(key, Duration.ofMinutes(properties.getRecentTtlMinutes()));
            }
        } catch (Exception ignored) {
            // Keep MySQL as source of truth.
        }
    }

    private List<RecentMemoryMessage> trimToBudget(List<RecentMemoryMessage> rows, int tokenBudget) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<RecentMemoryMessage> selected = new ArrayList<>();
        int used = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            RecentMemoryMessage row = rows.get(i);
            int tokens = row.tokenCount() == null ? 0 : row.tokenCount();
            if (!selected.isEmpty() && tokenBudget > 0 && used + tokens > tokenBudget) {
                break;
            }
            used += tokens;
            selected.add(row);
        }
        java.util.Collections.reverse(selected);
        return selected;
    }

    private boolean isRedisAvailable() {
        return properties.isRedisEnabled() && redisTemplate != null;
    }

    private String buildKey(Long userId, Long sessionId) {
        return properties.getRecentKeyPrefix() + ":" + userId + ":" + sessionId;
    }

    public record RecentWindowSnapshot(String source, List<RecentMemoryMessage> messages) {
    }
}
