package com.hm.badminton.dto.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured, mergeable constraints remembered for one AI conversation. */
@Data
public class AgentRequirement {
    private List<String> sportCodes = new ArrayList<>();
    private String city;
    private LocalDate targetDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Integer maxDistanceMeters;
    private String level;
    /** Normalized equipment category/keyword, for example "鞋" or "球拍". */
    private String equipmentKeyword;
    /** Soft requirements used by scoring and RAG, such as parking, lighting or beginner friendly. */
    private List<String> preferenceTags = new ArrayList<>();
    private List<String> avoidTags = new ArrayList<>();
    /** BALANCED, PRICE, DISTANCE, RATING or TIME. */
    private String sortPreference = "BALANCED";
    private boolean availabilityRequired = true;
    private boolean refundableRequired;
    /** Records which parser supplied a field, making merged cross-turn requirements explainable. */
    private Map<String, String> fieldSources = new LinkedHashMap<>();
    private List<String> intents = new ArrayList<>();
    private List<String> lastSelectedCardIds = new ArrayList<>();
}
