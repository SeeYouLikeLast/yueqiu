package com.hm.badminton.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hm.agent")
public class AgentProperties {
    private boolean enabled = true;
    private int maxHistoryMessages = 10;
    private int maxToolCalls = 6;
    private int memoryTtlMinutes = 60;
    private int rateLimitPerMinute = 10;
    private int toolTimeoutSeconds = 5;
    private int toolThreads = 6;
}
