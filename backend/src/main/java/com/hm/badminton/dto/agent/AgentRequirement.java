package com.hm.badminton.dto.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Structured, mergeable constraints remembered for one AI conversation. */
@Data
public class AgentRequirement {
    private List<String> sportCodes = new ArrayList<>();
    private LocalDate targetDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal maxBudget;
    private Integer maxDistanceMeters;
    private String level;
    /** Normalized equipment category/keyword, for example "鞋" or "球拍". */
    private String equipmentKeyword;
    private List<String> intents = new ArrayList<>();
    private List<String> lastSelectedCardIds = new ArrayList<>();
}
