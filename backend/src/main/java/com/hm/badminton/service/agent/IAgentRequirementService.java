package com.hm.badminton.service.agent;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;

public interface IAgentRequirementService {
    AgentRequirement load(Long conversationId);

    AgentRequirement merge(AgentRequirement previous, AgentChatRequest request, LoginUser loginUser);

    void cache(Long conversationId, AgentRequirement requirement);

    void evict(Long conversationId);
}
