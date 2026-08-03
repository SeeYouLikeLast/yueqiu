package com.hm.badminton.dto.agent;

/**
 * Whitelisted operations emitted by assistant quick actions.
 *
 * <p>A command selects an existing business workflow. It never contains mapper names,
 * SQL fragments or arbitrary tool identifiers supplied by the browser.</p>
 */
public enum AgentCommandType {
    FIND_NEARBY_PLACES,
    FIND_JOINABLE_ACTIVITIES,
    RECOMMEND_EQUIPMENT,
    VIEW_VENUE_PRODUCTS,
    FILTER_DISTANCE,
    FILTER_BUDGET,
    FILTER_PRICE,
    FILTER_LEVEL,
    VIEW_BOOKING_RULES,
    REFINE_RESULTS
}
