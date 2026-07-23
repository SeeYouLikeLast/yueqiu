package com.hm.badminton.service.agent;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;

public interface IAgentRequirementService {
    AgentRequirement load(AgentTurnContext turn);

    AgentRequirement merge(AgentRequirement previous, AgentChatRequest request, LoginUser loginUser);

    void cache(AgentTurnContext turn, AgentRequirement requirement);

    void evict(Long conversationId);
}
