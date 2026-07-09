package com.hm.badminton.service.agent.tools;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.social.ISocialService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActivityAgentTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final ISocialService socialService;

    public ActivityAgentTool(ISocialService socialService) {
        this.socialService = socialService;
    }

    @Tool(name = "searchJoinableActivities", description = "Search joinable city sport activities that are not full.")
    public List<AgentCard> searchJoinableActivities(String sportCode, String city, String level, Long excludeUserId) {
        PageResult<SportActivity> page = socialService.activities(blankToNull(sportCode), blankToNull(city), blankToNull(level), 1, 8);
        List<AgentCard> cards = new ArrayList<>();
        for (SportActivity activity : page.getRecords()) {
            if (excludeUserId != null && excludeUserId.equals(activity.getCreatorId())) {
                continue;
            }
            if (activity.getCurrentPlayers() != null && activity.getMaxPlayers() != null
                    && activity.getCurrentPlayers() >= activity.getMaxPlayers()) {
                continue;
            }
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_ACTIVITY);
            card.setTitle(activity.getTitle());
            card.setSubtitle(activity.getVenueName() + " · " + timeText(activity));
            card.setTags(List.of(activity.getCurrentPlayers() + "/" + activity.getMaxPlayers() + "人",
                    nullToText(activity.getLevelRequired()),
                    nullToText(activity.getFeeType())));
            card.setAction(AgentAction.confirm(AgentConstants.ACTION_JOIN_ACTIVITY, activity.getId()));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", activity.getId());
            meta.put("sportCode", activity.getSportCode());
            meta.put("venueName", activity.getVenueName());
            meta.put("startTime", activity.getStartTime());
            meta.put("endTime", activity.getEndTime());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private String timeText(SportActivity activity) {
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            return "近期可约";
        }
        return activity.getStartTime().format(FORMATTER) + "-" + activity.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToText(String value) {
        return value == null || value.isBlank() ? "不限" : value;
    }
}
