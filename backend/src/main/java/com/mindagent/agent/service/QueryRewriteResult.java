package com.mindagent.agent.service;

import java.util.List;

public record QueryRewriteResult(
        String originalQuery,
        String rewrittenQuery,
        List<String> keywords
) {
}
