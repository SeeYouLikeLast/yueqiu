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

    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        return request;
    }
}
