package com.mindbridge.agent.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBean(ChatModel.class)
public class SpringAiChatService implements ChatModelGateway {

    private final ChatModel chatModel;

    public SpringAiChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Flux<String> streamChat(List<Map<String, Object>> messages, String requestedModel) {
        Prompt prompt = buildPrompt(messages, requestedModel);
        return chatModel.stream(prompt)
                .map(this::extractContent)
                .filter(token -> token != null && !token.isEmpty());
    }

    @Override
    public Mono<String> completeOnce(List<Map<String, Object>> messages, String requestedModel) {
        Prompt prompt = buildPrompt(messages, requestedModel);
        return Mono.fromCallable(() -> extractContent(chatModel.call(prompt)))
                .defaultIfEmpty("");
    }

    private Prompt buildPrompt(List<Map<String, Object>> messages, String requestedModel) {
        List<Message> springMessages = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.getOrDefault("role", "user"));
            String content = String.valueOf(message.getOrDefault("content", ""));
            switch (role) {
                case "system" -> springMessages.add(new SystemMessage(content));
                case "assistant" -> springMessages.add(new AssistantMessage(content));
                default -> springMessages.add(new UserMessage(content));
            }
        }

        String model = requestedModel == null ? "" : requestedModel.trim();
        if (model.isEmpty()) {
            return new Prompt(springMessages);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();
        return new Prompt(springMessages, options);
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String content = response.getResult().getOutput().getText();
        return content == null ? "" : content;
    }
}

