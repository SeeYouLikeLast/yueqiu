package com.hm.badminton;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.agent.tools.ActivityAgentTool;
import com.hm.badminton.service.social.ISocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityAgentToolTest {

    private ISocialService socialService;
    private ActivityAgentTool tool;

    @BeforeEach
    void setUp() {
        socialService = mock(ISocialService.class);
        tool = new ActivityAgentTool(socialService);
    }

    @Test
    void intermediateUserCanJoinBeginnerThresholdActivityDuringOverlappingTime() {
        LocalDate today = LocalDate.now();
        mockActivities(activity(today, "初级以上"));

        List<AgentCard> cards = tool.searchJoinableActivities(
                "badminton", "西安市", "中级",
                today, LocalTime.of(19, 0), LocalTime.of(20, 0), 99L);

        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().getTitle()).isEqualTo("羽毛球下班对抗局");
    }

    @Test
    void unlimitedLevelStillRespectsTargetDate() {
        LocalDate today = LocalDate.now();
        mockActivities(activity(today, "中级对抗"));

        List<AgentCard> todayCards = tool.searchJoinableActivities(
                "badminton", "西安市", "不限",
                today, null, null, 99L);
        List<AgentCard> tomorrowCards = tool.searchJoinableActivities(
                "badminton", "西安市", "不限",
                today.plusDays(1), null, null, 99L);

        assertThat(todayCards).hasSize(1);
        assertThat(tomorrowCards).isEmpty();
    }

    private void mockActivities(SportActivity activity) {
        when(socialService.activities(
                eq("badminton"), eq("西安市"), isNull(), isNull(),
                eq("others"), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(activity), 1, 1, 50));
    }

    private SportActivity activity(LocalDate date, String levelRequired) {
        LocalDateTime startTime = date.atTime(19, 0);
        return new SportActivity()
                .setId(1L)
                .setCreatorId(2L)
                .setSportCode("badminton")
                .setVenueName("西安羽毛球馆")
                .setTitle("羽毛球下班对抗局")
                .setStartTime(startTime)
                .setEndTime(startTime.plusHours(2))
                .setMaxPlayers(6)
                .setCurrentPlayers(3)
                .setLevelRequired(levelRequired)
                .setFeeType("场地费均摊");
    }
}
