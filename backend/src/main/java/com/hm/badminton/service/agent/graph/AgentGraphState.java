package com.hm.badminton.service.agent.graph;

/** Shared key names used by the StateGraph and the request-scoped run context. */
public final class AgentGraphState {

    public static final String REQUEST_ID = "requestId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String REQUIREMENT_READY = "requirementReady";
    public static final String PLACE_CARDS = "placeCards";
    public static final String VENUE_PRODUCT_CARDS = "venueProductCards";
    public static final String ACTIVITY_CARDS = "activityCards";
    public static final String EQUIPMENT_CARDS = "equipmentCards";
    public static final String CANDIDATE_COUNT = "candidateCount";
    public static final String RAG_EVIDENCE_COUNT = "ragEvidenceCount";
    public static final String SCORED_COUNT = "scoredCount";
    public static final String SELECTED_COUNT = "selectedCount";
    public static final String RESPONSE = "response";

    private AgentGraphState() {
    }
}
