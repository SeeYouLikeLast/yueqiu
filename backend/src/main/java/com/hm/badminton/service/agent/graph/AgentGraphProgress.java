package com.hm.badminton.service.agent.graph;

import java.util.Map;

/** Event emitted by a graph node and forwarded to the browser through SSE. */
public record AgentGraphProgress(String event, Object data) {

    public static AgentGraphProgress stage(String code, String message) {
        return new AgentGraphProgress("stage", Map.of("code", code, "message", message));
    }
}
