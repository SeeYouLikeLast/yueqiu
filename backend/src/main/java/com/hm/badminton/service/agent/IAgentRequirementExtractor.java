package com.hm.badminton.service.agent;

import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentRequirementExtraction;

import java.util.Optional;

/** Optional model-based supplement for complex or low-confidence free text. */
@FunctionalInterface
public interface IAgentRequirementExtractor {
    Optional<AgentRequirementExtraction> extract(String message, AgentRequirement previous);
}
