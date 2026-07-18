package com.hm.badminton.service.agent.graph;

import com.hm.badminton.dto.agent.AgentCard;

import java.util.List;

/** Business operations executed by the nodes of the AI recommendation graph. */
public interface AgentGraphNodeHandler {

    void beginTurn(AgentGraphRunContext run);

    void understandRequirement(AgentGraphRunContext run);

    void dispatchTools(AgentGraphRunContext run);

    AgentPlaceBranchResult queryPlacesAndProducts(AgentGraphRunContext run);

    List<AgentCard> queryActivities(AgentGraphRunContext run);

    List<AgentCard> queryEquipment(AgentGraphRunContext run);

    void mergeCandidates(AgentGraphRunContext run,
                         List<AgentCard> places,
                         List<AgentCard> venueProducts,
                         List<AgentCard> activities,
                         List<AgentCard> equipment);

    void selectCandidates(AgentGraphRunContext run);

    void persistAnswer(AgentGraphRunContext run);
}
