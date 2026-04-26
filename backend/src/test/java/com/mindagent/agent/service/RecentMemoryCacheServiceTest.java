package com.mindagent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindagent.agent.config.ChatMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentMemoryCacheServiceTest {

    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    private RecentMemoryCacheService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ChatMemoryProperties properties = new ChatMemoryProperties();
        properties.setRecentKeyPrefix("mindagent:chat:recent");
        properties.setRecentKeepTurns(6);
        properties.setRecentTtlMinutes(1440);
        properties.setInputBudget(100);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        service = new RecentMemoryCacheService(properties, chatMessageService, provider, objectMapper);
    }

    @Test
    void shouldReturnCachedWindowWhenRedisHits() throws Exception {
        List<RecentMemoryMessage> cached = List.of(
                new RecentMemoryMessage(1L, "user", "hello", 10, LocalDateTime.now()),
                new RecentMemoryMessage(2L, "assistant", "world", 10, LocalDateTime.now())
        );
        when(valueOperations.get("mindagent:chat:recent:7:9")).thenReturn(objectMapper.writeValueAsString(cached));

        List<RecentMemoryMessage> result = service.loadRecentWindow(7L, 9L, 100);

        assertEquals(2, result.size());
        verify(chatMessageService, never()).loadRecentWindowFromDb(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void shouldFallbackToDbAndBackfillRedisWhenRedisMisses() {
        List<RecentMemoryMessage> dbRows = List.of(
                new RecentMemoryMessage(3L, "user", "q", 12, LocalDateTime.now())
        );
        when(valueOperations.get("mindagent:chat:recent:1:2")).thenReturn(null);
        when(chatMessageService.loadRecentWindowFromDb(1L, 2L, 88, 6)).thenReturn(dbRows);

        List<RecentMemoryMessage> result = service.loadRecentWindow(1L, 2L, 88);

        assertEquals(1, result.size());
        verify(valueOperations).set(eq("mindagent:chat:recent:1:2"), any());
        verify(redisTemplate).expire("mindagent:chat:recent:1:2", Duration.ofMinutes(1440));
    }

    @Test
    void shouldKeepSessionKeysIsolatedWhenRefreshing() throws Exception {
        List<RecentMemoryMessage> cached = List.of(
                new RecentMemoryMessage(1L, "user", "old", 10, LocalDateTime.now())
        );
        when(valueOperations.get("mindagent:chat:recent:1:10")).thenReturn(objectMapper.writeValueAsString(cached));
        when(valueOperations.get("mindagent:chat:recent:1:11")).thenReturn(null);
        when(chatMessageService.loadRecentWindowFromDb(1L, 11L, 100, 6)).thenReturn(List.of());

        service.refreshAfterAppend(1L, 10L, List.of(
                new RecentMemoryMessage(2L, "assistant", "new", 10, LocalDateTime.now())
        ));
        service.refreshAfterAppend(1L, 11L, List.of(
                new RecentMemoryMessage(3L, "assistant", "other", 10, LocalDateTime.now())
        ));

        verify(valueOperations).set(eq("mindagent:chat:recent:1:10"), any());
        verify(chatMessageService).loadRecentWindowFromDb(1L, 11L, 100, 6);
    }

    @Test
    void shouldDeleteCacheBySessionKey() {
        service.deleteRecentWindow(3L, 5L);
        verify(redisTemplate).delete("mindagent:chat:recent:3:5");
    }

    @Test
    void shouldTrimCachedMessagesByBudgetOrder() throws Exception {
        List<RecentMemoryMessage> cached = List.of(
                new RecentMemoryMessage(1L, "user", "first", 50, LocalDateTime.now()),
                new RecentMemoryMessage(2L, "assistant", "second", 30, LocalDateTime.now()),
                new RecentMemoryMessage(3L, "user", "third", 20, LocalDateTime.now())
        );
        when(valueOperations.get("mindagent:chat:recent:8:9")).thenReturn(objectMapper.writeValueAsString(cached));

        List<RecentMemoryMessage> result = service.loadRecentWindow(8L, 9L, 60);

        assertEquals(2, result.size());
        assertEquals(List.of(2L, 3L), result.stream().map(RecentMemoryMessage::messageId).toList());
        assertTrue(result.get(0).createdAt() != null);
    }
}
