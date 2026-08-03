package com.hm.badminton.service.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentRequirementExtraction;
import com.hm.badminton.service.agent.IAgentRequirementExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Uses DashScope only to extract a typed requirement patch from difficult free text.
 *
 * <p>The result is deliberately treated as untrusted input. Whitelisting and range
 * validation happen in {@link AgentRequirementService} before Graph tools can see it.</p>
 */
@Service
public class AgentRequirementModelExtractor implements IAgentRequirementExtractor {

    private static final Logger log = LoggerFactory.getLogger(AgentRequirementModelExtractor.class);
    private static final String EXTRACTION_PROMPT = """
            你是“约个球”平台的需求提取器，只负责从本轮用户文本中提取明确表达的约束。
            不要补充用户没有说过的价格、距离、日期、时间、水平或偏好。
            allowed intents: PLACE, ACTIVITY, EQUIPMENT, BOOKING_RULES
            allowed sportCodes: badminton, table_tennis, football, basketball, tennis, volleyball
            allowed level: 不限, 初级, 中级, 高级
            allowed sortPreference: BALANCED, PRICE, DISTANCE, RATING, TIME, VALUE
            日期使用 yyyy-MM-dd，时间使用 HH:mm。未知字段返回 null 或空数组。
            confidence 是 0 到 1，表示对本轮提取结果的置信度。
            只返回 JSON，不要 Markdown，不要解释：
            {
              "confidence":0.0,
              "intents":[],
              "sportCodes":[],
              "city":null,
              "targetDate":null,
              "startTime":null,
              "endTime":null,
              "durationMinutes":null,
              "minBudget":null,
              "maxBudget":null,
              "maxDistanceMeters":null,
              "level":null,
              "equipmentKeyword":null,
              "preferenceTags":[],
              "avoidTags":[],
              "sortPreference":null,
              "availabilityRequired":null,
              "refundableRequired":null
            }
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentRequirementModelExtractor(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                          ObjectMapper objectMapper,
                                          AgentProperties properties) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<AgentRequirementExtraction> extract(String message, AgentRequirement previous) {
        if (!properties.isRequirementModelExtractionEnabled() || message == null || message.isBlank()) {
            return Optional.empty();
        }
        ChatClient.Builder builder = safeBuilder();
        if (builder == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("currentMessage", message);
            payload.put("previousRequirement", previous == null ? new AgentRequirement() : previous);
            String content = builder.build()
                    .prompt()
                    .system(EXTRACTION_PROMPT)
                    .user(objectMapper.writeValueAsString(payload))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }
            AgentRequirementExtraction extraction = objectMapper.readValue(
                    stripJsonFence(content), AgentRequirementExtraction.class);
            if (extraction.getConfidence() == null
                    || extraction.getConfidence() < properties.getRequirementModelMinConfidence()) {
                log.debug("Agent requirement extraction ignored due to low confidence: {}",
                        extraction.getConfidence());
                return Optional.empty();
            }
            return Optional.of(extraction);
        } catch (Exception ex) {
            // Rule parsing remains the deterministic fallback when the model is unavailable or malformed.
            log.warn("Agent requirement extraction failed, using rule result: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private ChatClient.Builder safeBuilder() {
        try {
            return chatClientBuilderProvider.getIfAvailable();
        } catch (BeansException ex) {
            return null;
        }
    }

    private String stripJsonFence(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }
}
