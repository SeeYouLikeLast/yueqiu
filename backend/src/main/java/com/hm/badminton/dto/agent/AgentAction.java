package com.hm.badminton.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentAction {
    private String type;
    private String id;
    private boolean requireConfirm;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static AgentAction of(String type, Object id) {
        return new AgentAction(type, id == null ? null : String.valueOf(id), false, new LinkedHashMap<>());
    }

    public static AgentAction confirm(String type, Object id) {
        return new AgentAction(type, id == null ? null : String.valueOf(id), true, new LinkedHashMap<>());
    }
}
