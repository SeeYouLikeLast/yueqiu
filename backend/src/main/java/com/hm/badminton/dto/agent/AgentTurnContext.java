package com.hm.badminton.dto.agent;

/** Identifies the short database transaction that starts an AI turn. */
public record AgentTurnContext(Long conversationId, Long userId) {
}
