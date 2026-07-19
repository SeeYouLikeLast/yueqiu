package com.hm.badminton.service.agent.tools;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.social.ISocialService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    public List<AgentCard> searchJoinableActivities(String sportCode,
                                                    String city,
                                                    String level,
                                                    LocalDate targetDate,
                                                    LocalTime startTime,
                                                    LocalTime endTime,
                                                    Long excludeUserId) {
        // 先取同城、同运动的有效活动，再在工具层做“等级兼容”和“时段重叠”判断。
        // SQL 等值查询无法表达“中级用户可以参加初级以上活动”，也不能把“不限”当成无筛选。
        PageResult<SportActivity> page = socialService.activities(
                blankToNull(sportCode),
                blankToNull(city),
                null,
                null,
                "others",
                1,
                50);
        List<AgentCard> cards = new ArrayList<>();
        for (SportActivity activity : page.getRecords()) {
            if (excludeUserId != null && excludeUserId.equals(activity.getCreatorId())) {
                continue;
            }
            if (activity.getCurrentPlayers() != null && activity.getMaxPlayers() != null
                    && activity.getCurrentPlayers() >= activity.getMaxPlayers()) {
                continue;
            }
            if (!levelMatches(level, activity.getLevelRequired())
                    || !timeMatches(activity, targetDate, startTime, endTime)) {
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
        if (hasSpecificLevel(level) && levelMatches(level, activity.getLevelRequired())) {
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
        if (hasSpecificLevel(level) && levelMatches(level, activity.getLevelRequired())) {
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

    /**
     * 判断活动是否覆盖用户目标时段。活动 19:00-21:00 可以匹配用户提出的 19:00-20:00，
     * 但不会匹配上午时段；只有日期时则要求活动在该日期开始。
     */
    private boolean timeMatches(SportActivity activity,
                                LocalDate targetDate,
                                LocalTime startTime,
                                LocalTime endTime) {
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            return targetDate == null && startTime == null && endTime == null;
        }
        LocalDate activityDate = activity.getStartTime().toLocalDate();
        if (targetDate != null && !targetDate.equals(activityDate)) {
            return false;
        }
        if (startTime == null && endTime == null) {
            return true;
        }
        LocalDate queryDate = targetDate == null ? activityDate : targetDate;
        LocalTime queryStartTime = startTime == null ? LocalTime.MIN : startTime;
        LocalTime queryEndTime = endTime == null
                ? (startTime == null ? LocalTime.MAX : startTime.plusHours(1))
                : endTime;
        LocalDateTime queryStart = queryDate.atTime(queryStartTime);
        LocalDateTime queryEnd = queryDate.atTime(queryEndTime);
        return activity.getStartTime().isBefore(queryEnd) && activity.getEndTime().isAfter(queryStart);
    }

    /** “初级以上/中级对抗”表示最低门槛，而不是必须与用户等级文本完全相同。 */
    private boolean levelMatches(String userLevel, String requiredLevel) {
        if (!hasSpecificLevel(userLevel) || requiredLevel == null || requiredLevel.isBlank()
                || requiredLevel.contains("不限") || requiredLevel.contains("新手友好")) {
            return true;
        }
        int userRank = levelRank(userLevel);
        int requiredRank = levelRank(requiredLevel);
        return userRank == 0 || requiredRank == 0 || userRank >= requiredRank;
    }

    private boolean hasSpecificLevel(String level) {
        return level != null && !level.isBlank() && !level.contains("不限");
    }

    private int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        if (level.contains("高级") || level.contains("高手") || level.contains("进阶")) {
            return 3;
        }
        if (level.contains("中级")) {
            return 2;
        }
        if (level.contains("初级") || level.contains("新手") || level.contains("入门")) {
            return 1;
        }
        return 0;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToText(String value) {
        return value == null || value.isBlank() ? "不限" : value;
    }
}
