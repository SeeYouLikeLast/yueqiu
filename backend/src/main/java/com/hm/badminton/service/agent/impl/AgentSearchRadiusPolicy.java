package com.hm.badminton.service.agent.impl;

import java.util.ArrayList;
import java.util.List;

/** Defines the bounded radius sequence used when a nearby place search has too few results. */
public final class AgentSearchRadiusPolicy {

    private static final int DEFAULT_RADIUS_METERS = 8_000;
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final List<Integer> EXPANSION_STEPS = List.of(8_000, 15_000, 30_000, 50_000);

    private AgentSearchRadiusPolicy() {
    }

    public static List<Integer> candidates(Integer requestedRadiusMeters) {
        int initial = requestedRadiusMeters == null
                ? DEFAULT_RADIUS_METERS
                : Math.max(500, Math.min(requestedRadiusMeters, MAX_RADIUS_METERS));
        List<Integer> radii = new ArrayList<>();
        radii.add(initial);
        for (Integer step : EXPANSION_STEPS) {
            if (step > initial) {
                radii.add(step);
            }
        }
        return radii;
    }
}
