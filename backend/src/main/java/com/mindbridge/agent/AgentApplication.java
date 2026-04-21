package com.mindbridge.agent;

import com.mindbridge.agent.config.RagProperties;
import com.mindbridge.agent.config.VllmProperties;
import com.mindbridge.agent.config.ChatMemoryProperties;
import com.mindbridge.agent.config.McpProperties;
import com.mindbridge.agent.config.AuthProperties;
import com.mindbridge.agent.config.LlmProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        VllmProperties.class,
        RagProperties.class,
        ChatMemoryProperties.class,
        McpProperties.class,
        AuthProperties.class,
        LlmProviderProperties.class
})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
