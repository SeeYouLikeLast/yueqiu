package com.hm.badminton.dto.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict model output. Business facts are rendered later from validated cards. */
@Data
public class AgentModelDecision {
    private List<String> selectedCardIds = new ArrayList<>();
    /** Card-specific natural-language reasons; keys must be selected card ids. */
    private Map<String, String> recommendationReasons = new LinkedHashMap<>();
    private String explanation;
}
