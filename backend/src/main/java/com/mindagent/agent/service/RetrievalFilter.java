package com.mindagent.agent.service;

import java.util.Set;

public record RetrievalFilter(
        Set<String> knowledgeBaseKeys,
        Set<String> categories,
        Set<String> sourceTypes
) {

    public static RetrievalFilter empty() {
        return new RetrievalFilter(Set.of(), Set.of(), Set.of());
    }
}
