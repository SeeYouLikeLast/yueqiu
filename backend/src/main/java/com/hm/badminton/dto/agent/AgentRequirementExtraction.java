package com.hm.badminton.dto.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Untrusted JSON patch extracted by the model from one free-text message.
 *
 * <p>Dates and times remain strings at this boundary. The requirement service parses,
 * clamps and whitelists all values before they can affect a business query.</p>
 */
@Data
public class AgentRequirementExtraction {
    private Double confidence;
    private List<String> intents = new ArrayList<>();
    private List<String> sportCodes = new ArrayList<>();
    private String city;
    private String targetDate;
    private String startTime;
    private String endTime;
    private Integer durationMinutes;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private Integer maxDistanceMeters;
    private String level;
    private String equipmentKeyword;
    private List<String> preferenceTags = new ArrayList<>();
    private List<String> avoidTags = new ArrayList<>();
    private String sortPreference;
    private Boolean availabilityRequired;
    private Boolean refundableRequired;
}
