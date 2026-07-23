package com.hm.badminton.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A traceable soft-knowledge excerpt retrieved for one validated business card. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRagEvidence {
    private String sourceType;
    private Long sourceId;
    private String title;
    private String excerpt;
    private double relevance;
}
