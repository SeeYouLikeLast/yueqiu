package com.hm.badminton;

import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.service.agent.impl.AgentClarificationResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClarificationResolverTest {

    @Test
    void shouldAskForMissingBudgetAndKeepShoeContext() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));
        requirement.setEquipmentKeyword("鞋");

        AgentClarificationResolver.Clarification result =
                AgentClarificationResolver.budget("帮我按预算筛装备（羽毛球）", requirement);

        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("预算控制在多少", "羽毛球鞋");
        assertThat(result.quickReplies()).contains("500元以内的羽毛球鞋");
    }

    @Test
    void rememberedBudgetShouldContinueWithoutClarification() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setMaxBudget(BigDecimal.valueOf(500));

        assertThat(AgentClarificationResolver.budget("继续按预算筛选", requirement)).isNull();
    }

    @Test
    void placeBudgetRequestShouldNotBeTreatedAsEquipmentClarification() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setIntents(List.of("PLACE"));

        assertThat(AgentClarificationResolver.budget("帮我按预算筛场馆团购", requirement)).isNull();
    }

    @Test
    void emptyEquipmentResultShouldNotAskForAnotherBudgetAfterAutomaticExpansion() {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));
        requirement.setIntents(List.of("EQUIPMENT"));
        requirement.setEquipmentKeyword("鞋");
        requirement.setMaxBudget(BigDecimal.valueOf(200));

        AgentClarificationResolver.Clarification result =
                AgentClarificationResolver.equipmentNoResult(requirement);

        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("200元以内的羽毛球鞋", "自动检查价格更高的同类商品");
        assertThat(result.answer()).doesNotContain("扩大附近范围");
        assertThat(result.quickReplies()).containsExactly("查看不限品类装备", "推荐新手装备");
        assertThat(result.quickReplies()).noneMatch(text -> text.contains("500元"));
    }
}
