package com.hm.badminton.service.agent;

import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRequirement;

import java.util.List;

/** Retrieves traceable subjective knowledge and attaches it only to matching business cards. */
public interface IAgentRagService {

    List<AgentCard> enrich(String query,
                           String city,
                           AgentRequirement requirement,
                           List<AgentCard> cards);
}
