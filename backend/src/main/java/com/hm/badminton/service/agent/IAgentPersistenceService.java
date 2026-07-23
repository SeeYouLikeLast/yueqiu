package com.hm.badminton.service.agent;

import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentConversationView;
import com.hm.badminton.dto.agent.AgentMessageView;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;

import java.util.List;

/** Keeps AI database writes in short transactions, outside external API calls. */
public interface IAgentPersistenceService {
    AgentTurnContext beginTurn(Long userId, String anonymousId, AgentChatRequest request);

    void completeTurn(AgentTurnContext turn,
                      String answer,
                      List<AgentCard> cards,
                      AgentRequirement requirement);

    List<AgentConversationView> conversations(Long userId, String anonymousId);

    List<AgentMessageView> messages(Long userId, String anonymousId, Long conversationId);

    void deleteConversation(Long userId, String anonymousId, Long conversationId);

    /** Returns the durable structured requirement for either MySQL or Redis conversations. */
    String requirementsJson(AgentTurnContext turn);
}
