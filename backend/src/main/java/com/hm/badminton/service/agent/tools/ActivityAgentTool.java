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
            meta.put("available", vacancy(activity) > 0);
            meta.put("stock", vacancy(activity));
            meta.put("suitableLevel", nullToText(activity.getLevelRequired()));
            meta.put("sceneTags", List.of(nullToText(activity.getLevelRequired()), nullToText(activity.getFeeType()), timeText(activity)));
            meta.put("pros", pros(activity, level));
            meta.put("cons", cons(activity));
            meta.put("recommendScore", activityScore(activity, level));
            meta.put("recommendReasons", pros(activity, level).stream().limit(3).toList());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private int vacancy(SportActivity activity) {
        if (activity.getMaxPlayers() == null || activity.getCurrentPlayers() == null) {
            return 0;
        }
        return Math.max(0, activity.getMaxPlayers() - activity.getCurrentPlayers());
    }

    private List<String> pros(SportActivity activity, String level) {
        List<String> pros = new ArrayList<>();
        if (vacancy(activity) > 0) {
            pros.add("还有空位，可以申请加入");
        }
        if (level != null && !level.isBlank() && level.equals(activity.getLevelRequired())) {
            pros.add("水平要求与你匹配");
        } else if (activity.getLevelRequired() == null || activity.getLevelRequired().isBlank() || "不限".equals(activity.getLevelRequired())) {
            pros.add("水平要求宽松");
        }
        if (activity.getStartTime() != null) {
            pros.add("时间明确：" + timeText(activity));
        }
        return pros;
    }

    private List<String> cons(SportActivity activity) {
        List<String> cons = new ArrayList<>();
        if (vacancy(activity) <= 1) {
            cons.add("剩余名额较少");
        }
        if (activity.getFeeType() == null || activity.getFeeType().isBlank()) {
            cons.add("费用方式需要进一步确认");
        }
        return cons;
    }

    private int activityScore(SportActivity activity, String level) {
        int score = 50;
        int vacancy = vacancy(activity);
        if (vacancy >= 3) {
            score += 15;
        } else if (vacancy > 0) {
            score += 8;
        }
        if (level != null && !level.isBlank() && level.equals(activity.getLevelRequired())) {
            score += 20;
        } else if (activity.getLevelRequired() == null || activity.getLevelRequired().isBlank() || "不限".equals(activity.getLevelRequired())) {
            score += 12;
        }
        if (activity.getStartTime() != null) {
            score += 10;
        }
        if (activity.getFeeType() != null && !activity.getFeeType().isBlank()) {
            score += 5;
        }
        return Math.min(100, score);
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
