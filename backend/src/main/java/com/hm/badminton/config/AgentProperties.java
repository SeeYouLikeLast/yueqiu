package com.hm.badminton.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hm.agent")
public class AgentProperties {
    private boolean enabled = true;
    private int memoryTtlMinutes = 60;
    private int rateLimitPerMinute = 10;
    private int toolTimeoutSeconds = 5;
    private int toolThreads = 6;
    private int anonymousHistoryTtlDays = 7;
    private int anonymousMaxConversations = 30;
    private int anonymousMaxMessagesPerConversation = 100;
    private boolean ragEnabled = true;
    private boolean ragEmbeddingEnabled = true;
    private int ragCandidateLimit = 48;
    private int ragTopK = 3;
    private int ragEmbeddingCacheDays = 7;
    /** Use the model only when deterministic rules report a complex or ambiguous message. */
    private boolean requirementModelExtractionEnabled = true;
    private int requirementModelMinLength = 24;
    private double requirementModelMinConfidence = 0.55d;
}
