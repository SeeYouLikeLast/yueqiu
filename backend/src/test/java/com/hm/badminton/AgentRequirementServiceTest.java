package com.hm.badminton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.service.agent.impl.AgentRequirementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRequirementServiceTest {

    private final AgentRequirementService service = new AgentRequirementService(
            null, null, new ObjectMapper(), new AgentProperties());

    @Test
    void dateMustNotBeParsedAsTimeRange() {
        AgentRequirement result = service.merge(new AgentRequirement(), request("2026-07-20 打羽毛球"), null);

        assertThat(result.getTargetDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.getStartTime()).isNull();
        assertThat(result.getEndTime()).isNull();
    }

    @Test
    void shouldExtractDateTimeAndBudget() {
        AgentRequirement result = service.merge(new AgentRequirement(),
                request("2026-07-20 19:00-21:00 打羽毛球，预算 80 元以内"), null);

        assertThat(result.getSportCodes()).containsExactly("badminton");
        assertThat(result.getTargetDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(result.getMaxBudget()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void followUpShouldKeepOldConstraintsAndReplaceBudget() {
        AgentRequirement previous = new AgentRequirement();
        previous.setSportCodes(List.of("badminton"));
        previous.setTargetDate(LocalDate.of(2026, 7, 20));
        previous.setStartTime(LocalTime.of(19, 0));
        previous.setEndTime(LocalTime.of(21, 0));

        AgentRequirement result = service.merge(previous, request("换成 50 元以内的"), null);

        assertThat(result.getSportCodes()).containsExactly("badminton");
        assertThat(result.getTargetDate()).isEqualTo(previous.getTargetDate());
        assertThat(result.getStartTime()).isEqualTo(previous.getStartTime());
        assertThat(result.getMaxBudget()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void explicitAllSportsShouldClearPreviousSportFilter() {
        AgentRequirement previous = new AgentRequirement();
        previous.setSportCodes(List.of("badminton"));
        AgentChatRequest request = request("推荐新手装备（不限球类）");
        request.setAllSportsRequested(true);

        AgentRequirement result = service.merge(previous, request, null);

        assertThat(result.getSportCodes()).isEmpty();
    }

    @Test
    void explicitSportShouldWinWhenClientAlsoSendsAllSportsFlag() {
        AgentChatRequest request = request("今晚附近能打球吗（羽毛球）");
        request.setSportCodes(List.of("badminton"));
        request.setAllSportsRequested(true);

        AgentRequirement result = service.merge(new AgentRequirement(), request, null);

        assertThat(result.getSportCodes()).containsExactly("badminton");
    }

    @Test
    void shoePurchaseShouldSelectBadmintonEquipmentBranch() {
        AgentRequirement result = service.merge(new AgentRequirement(), request("我要买一个羽毛球鞋子"), null);

        assertThat(result.getSportCodes()).containsExactly("badminton");
        assertThat(result.getIntents()).contains("EQUIPMENT");
        assertThat(result.getEquipmentKeyword()).isEqualTo("鞋");
    }

    @Test
    void budgetFollowUpShouldKeepPreviousEquipmentCategory() {
        AgentRequirement previous = service.merge(
                new AgentRequirement(), request("我要买一个羽毛球鞋子"), null);

        AgentRequirement result = service.merge(previous, request("帮我按预算筛装备（羽毛球）"), null);

        assertThat(result.getEquipmentKeyword()).isEqualTo("鞋");
        assertThat(result.getMaxBudget()).isNull();
    }

    @Test
    void nearbyExpansionFollowUpMustNotSwitchEquipmentToVenueProducts() {
        AgentRequirement previous = service.merge(
                new AgentRequirement(), request("200元以内的羽毛球鞋"), null);

        AgentRequirement result = service.merge(previous, request("帮我扩大附近范围（羽毛球）"), null);

        assertThat(result.getIntents()).containsExactly("EQUIPMENT");
        assertThat(result.getEquipmentKeyword()).isEqualTo("鞋");
        assertThat(result.getMaxBudget()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    void equipmentRequestShouldNotInheritVenueBudget() {
        AgentRequirement previous = new AgentRequirement();
        previous.setIntents(List.of("PLACE"));
        previous.setMaxBudget(BigDecimal.valueOf(50));

        AgentRequirement result = service.merge(previous, request("我要买一个羽毛球鞋子"), null);

        assertThat(result.getMaxBudget()).isNull();
        assertThat(result.getIntents()).containsExactly("EQUIPMENT");
    }

    @Test
    void explicitRequestCanReuseBudgetAcrossDomains() {
        AgentRequirement previous = new AgentRequirement();
        previous.setIntents(List.of("PLACE"));
        previous.setMaxBudget(BigDecimal.valueOf(300));

        AgentRequirement result = service.merge(previous, request("按同样预算买羽毛球鞋"), null);

        assertThat(result.getMaxBudget()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void activityQuickReplyShouldCarryExecutableDateAndTime() {
        AgentRequirement result = service.merge(new AgentRequirement(),
                request("查看今天19:00可加入的局（羽毛球）"), null);

        assertThat(result.getIntents()).contains("ACTIVITY");
        assertThat(result.getSportCodes()).containsExactly("badminton");
        assertThat(result.getTargetDate()).isEqualTo(LocalDate.now());
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void unlimitedLevelQuickReplyShouldClearExactLevelRestriction() {
        AgentRequirement previous = new AgentRequirement();
        previous.setLevel("中级");

        AgentRequirement result = service.merge(previous,
                request("不限水平查看今天可加入的局（羽毛球）"), null);

        assertThat(result.getIntents()).contains("ACTIVITY");
        assertThat(result.getLevel()).isEqualTo("不限");
        assertThat(result.getTargetDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldExtractCityDistancePreferencesAndSortOrder() {
        AgentRequirement result = service.merge(new AgentRequirement(),
                request("北京 3 公里以内找一个离我最近、能停车、不要拥挤的羽毛球馆"), null);

        assertThat(result.getCity()).isEqualTo("北京市");
        assertThat(result.getMaxDistanceMeters()).isEqualTo(3000);
        assertThat(result.getPreferenceTags()).contains("停车");
        assertThat(result.getPreferenceTags()).contains("不拥挤");
        assertThat(result.getSortPreference()).isEqualTo("DISTANCE");
        assertThat(result.getFieldSources()).containsKeys("city", "maxDistanceMeters", "preferences", "sortPreference");
    }

    @Test
    void distanceRefinementShouldKeepPreviousPlaceIntent() {
        AgentRequirement previous = new AgentRequirement();
        previous.setSportCodes(List.of("badminton"));
        previous.setIntents(List.of("PLACE"));
        previous.setTargetDate(LocalDate.now());
        previous.setStartTime(LocalTime.of(19, 0));
        previous.setEndTime(LocalTime.of(20, 0));

        AgentRequirement result = service.merge(previous, request("帮我按距离重新筛（羽毛球）"), null);

        assertThat(result.getSportCodes()).containsExactly("badminton");
        assertThat(result.getIntents()).containsExactly("PLACE");
        assertThat(result.getEquipmentKeyword()).isNull();
        assertThat(result.getSortPreference()).isEqualTo("DISTANCE");
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void shouldSupportMinimumBudgetAndRangeExpansionFollowUp() {
        AgentRequirement initial = service.merge(new AgentRequirement(), request("至少 200 元的羽毛球鞋，5 公里以内"), null);

        AgentRequirement expanded = service.merge(initial, request("帮我扩大附近范围"), null);

        assertThat(initial.getMinBudget()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(expanded.getMaxDistanceMeters()).isEqualTo(10000);
        assertThat(expanded.getEquipmentKeyword()).isEqualTo("鞋");
    }

    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        return request;
    }
}
