package com.hm.badminton.dto.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured payload sent by a trusted quick-action UI.
 *
 * <p>The backend still validates every field before merging it into conversation memory.
 * Nullable fields mean "keep the previous value"; explicit clear flags remove a filter.</p>
 */
@Data
public class AgentCommand {
    private AgentCommandType type;
    private List<String> sportCodes = new ArrayList<>();
    private boolean allSportsRequested;
    private AgentTimePreset timePreset;
    private LocalDate targetDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private boolean clearBudget;
    private Integer maxDistanceMeters;
    private String level;
    private String equipmentKeyword;
    private boolean clearEquipmentKeyword;
    private String sortPreference;
    private List<String> preferenceTags = new ArrayList<>();
    private List<String> avoidTags = new ArrayList<>();
    private Boolean availabilityRequired;
    private Boolean refundableRequired;
}
