package com.hm.badminton.service.agent.graph;

import com.hm.badminton.dto.agent.AgentCard;

import java.util.List;

/** Output of the dependent place -> venue product graph branch. */
public record AgentPlaceBranchResult(List<AgentCard> places, List<AgentCard> venueProducts) {

    public AgentPlaceBranchResult {
        places = places == null ? List.of() : List.copyOf(places);
        venueProducts = venueProducts == null ? List.of() : List.copyOf(venueProducts);
    }

    public static AgentPlaceBranchResult empty() {
        return new AgentPlaceBranchResult(List.of(), List.of());
    }
}
