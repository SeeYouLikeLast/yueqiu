package com.hm.badminton.dto.agent;

/** Identifies one persisted turn. Logged-in turns use MySQL; anonymous turns use Redis. */
public record AgentTurnContext(Long conversationId, Long userId, String anonymousId) {

    public boolean anonymous() {
        return userId == null || userId <= 0;
    }
}
