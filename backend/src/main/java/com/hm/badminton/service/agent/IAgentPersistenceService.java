package com.hm.badminton.service.agent;

import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;

import java.util.List;

/** Keeps AI database writes in short transactions, outside external API calls. */
public interface IAgentPersistenceService {
    AgentTurnContext beginTurn(Long userId, AgentChatRequest request);

    void completeTurn(Long conversationId,
                      Long userId,
                      String answer,
                      List<AgentCard> cards,
                      AgentRequirement requirement);
}
