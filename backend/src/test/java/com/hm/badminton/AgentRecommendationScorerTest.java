package com.hm.badminton;

import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.service.agent.impl.AgentRecommendationScorer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRecommendationScorerTest {

    private final AgentRecommendationScorer scorer = new AgentRecommendationScorer();

    @Test
    void shouldPreferAvailableEquipmentWithinBudgetAndLevel() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));
        requirement.setMaxBudget(BigDecimal.valueOf(200));
        requirement.setLevel("初级");
        requirement.setSortPreference("VALUE");

        AgentCard beginner = equipment("beginner", "¥129", 4.5, 30, "初学者");
        AgentCard expensive = equipment("expensive", "¥399", 4.9, 30, "进阶");

        List<AgentCard> ranked = scorer.scoreAndSort(List.of(expensive, beginner), requirement);

        assertThat(ranked.getFirst().getCardId()).isEqualTo("beginner");
        assertThat(ranked.getFirst().getMeta()).containsKeys("recommendScore", "scoreBreakdown", "rankingVersion");
        assertThat(score(ranked.getFirst())).isGreaterThan(score(ranked.getLast()));
    }

    @Test
    void shouldUseRagEvidenceAsOneSoftRankingSignal() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));

        AgentCard supported = equipment("supported", "¥199", 4.5, 20, "初学者");
        supported.getMeta().put("knowledgeScore", 95);
        AgentCard unknown = equipment("unknown", "¥199", 4.5, 20, "初学者");

        List<AgentCard> ranked = scorer.scoreAndSort(List.of(unknown, supported), requirement);

        assertThat(ranked.getFirst().getCardId()).isEqualTo("supported");
        assertThat(score(supported)).isGreaterThan(score(unknown));
    }

    private AgentCard equipment(String id, String price, double rating, int stock, String level) {
        AgentCard card = new AgentCard();
        card.setCardId(id);
        card.setType(AgentConstants.CARD_EQUIPMENT);
        card.setTitle(id);
        card.setPrice(price);
        card.setTags(List.of("羽毛球鞋"));
        card.setMeta(new LinkedHashMap<>());
        card.getMeta().put("sportCode", "badminton");
        card.getMeta().put("available", stock > 0);
        card.getMeta().put("stock", stock);
        card.getMeta().put("rating", rating);
        card.getMeta().put("suitableLevel", level);
        return card;
    }

    private int score(AgentCard card) {
        return ((Number) card.getMeta().get("recommendScore")).intValue();
    }
}
