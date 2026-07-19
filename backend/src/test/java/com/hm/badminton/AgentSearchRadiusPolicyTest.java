package com.hm.badminton;

import com.hm.badminton.service.agent.impl.AgentSearchRadiusPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSearchRadiusPolicyTest {

    @Test
    void defaultSearchShouldExpandInBoundedSteps() {
        assertThat(AgentSearchRadiusPolicy.candidates(null))
                .containsExactly(8_000, 15_000, 30_000, 50_000);
    }

    @Test
    void explicitSmallRadiusShouldBeKeptBeforeExpansion() {
        assertThat(AgentSearchRadiusPolicy.candidates(2_000))
                .containsExactly(2_000, 8_000, 15_000, 30_000, 50_000);
    }

    @Test
    void radiusMustNeverExpandPastFiftyKilometers() {
        assertThat(AgentSearchRadiusPolicy.candidates(60_000))
                .containsExactly(50_000);
    }
}
