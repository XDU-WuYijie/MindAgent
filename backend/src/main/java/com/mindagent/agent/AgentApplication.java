package com.mindagent.agent;

import com.mindagent.agent.config.RagProperties;
import com.mindagent.agent.config.VllmProperties;
import com.mindagent.agent.config.ChatMemoryProperties;
import com.mindagent.agent.config.McpProperties;
import com.mindagent.agent.config.AuthProperties;
import com.mindagent.agent.config.LlmProviderProperties;
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
