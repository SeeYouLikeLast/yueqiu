package com.hm.badminton.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCard {
    /** Stable reference exposed to the model, never rendered as user-facing text. */
    private String cardId;
    private String type;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String price;
    private List<String> tags = new ArrayList<>();
    private AgentAction action;
    private Map<String, Object> meta = new LinkedHashMap<>();
}
