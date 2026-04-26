package com.mindagent.agent.service;

import org.springframework.stereotype.Service;

@Service
public class TokenEstimateService {

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkChars = 0;
        int latinChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                cjkChars++;
            } else {
                latinChars++;
            }
        }
        return Math.max(1, cjkChars + (int) Math.ceil(latinChars / 4.0));
    }
}
