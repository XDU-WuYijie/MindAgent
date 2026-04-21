package com.mindbridge.agent.service;

import java.util.HashMap;
import java.util.Map;

public final class ChatMessageFactory {

    private ChatMessageFactory() {
    }

    public static Map<String, Object> message(String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
}

