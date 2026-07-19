package com.hm.badminton.service.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.entity.AgentConversation;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.service.agent.IAgentRequirementService;
import com.hm.badminton.service.agent.tools.EquipmentQueryNormalizer;
import com.hm.badminton.utils.RedisTtl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts every turn into mergeable constraints instead of concatenating raw messages. */
@Service
public class AgentRequirementService implements IAgentRequirementService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})月(\\d{1,2})日?(?!\\d)");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(?<!\\d)(2[0-3]|[01]?\\d)(?:[:：点时]([0-5]?\\d)?)?\\s*(?:-|到|至|~|—)\\s*(2[0-3]|[01]?\\d)(?:[:：点时]([0-5]?\\d)?)?(?!\\d)");
    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile(
            "(?<!\\d)(2[0-3]|[01]?\\d)(?:[:：]([0-5]\\d)|点(?:([0-5]?\\d)分?)?|时)(?!\\d)");
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(?:预算|价格|最多|不超过)?\\s*(\\d{1,5})\\s*(?:元|块|以内|以下|左右)");
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(公里|千米|km|米|m)", Pattern.CASE_INSENSITIVE);

    private final AgentConversationMapper conversationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentRequirementService(AgentConversationMapper conversationMapper,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   AgentProperties properties) {
        this.conversationMapper = conversationMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AgentRequirement load(Long conversationId) {
        String key = RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            AgentConversation conversation = conversationMapper.selectById(conversationId);
            json = conversation == null ? null : conversation.getRequirementsJson();
        }
        AgentRequirement requirement = read(json);
        if (requirement.getTargetDate() != null && requirement.getTargetDate().isBefore(LocalDate.now())) {
            requirement.setTargetDate(null);
            requirement.setStartTime(null);
            requirement.setEndTime(null);
        }
        return requirement;
    }

    @Override
    public AgentRequirement merge(AgentRequirement previous, AgentChatRequest request, LoginUser loginUser) {
        AgentRequirement merged = copy(previous);
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        Set<String> currentIntents = inferIntents(message);
        Set<String> previousIntents = new LinkedHashSet<>(
                merged.getIntents() == null ? List.of() : merged.getIntents());
        // “扩大附近范围”是对上一轮结果的操作，不应仅因包含“附近”就把装备会话切成场所团购。
        if (isRangeExpansionFollowUp(message) && !previousIntents.isEmpty()) {
            currentIntents = previousIntents;
        }

        clearStaleCrossDomainBudget(merged, currentIntents, message);
        mergeSports(merged, request, message);
        mergeEquipmentKeyword(merged, currentIntents, message);
        mergeDateAndTime(merged, message);
        mergeBudget(merged, message);
        mergeDistance(merged, message);
        mergeLevel(merged, message, loginUser);
        mergeIntents(merged, currentIntents);
        return merged;
    }

    private boolean isRangeExpansionFollowUp(String message) {
        return containsAny(message, "扩大附近范围", "扩大范围", "范围再大", "找远一点", "更远一点");
    }

    /** Keeps the concrete equipment category across a follow-up such as "按预算筛选". */
    private void mergeEquipmentKeyword(AgentRequirement target,
                                       Set<String> currentIntents,
                                       String message) {
        if (!currentIntents.contains("EQUIPMENT")) {
            return;
        }
        if (isRangeExpansionFollowUp(message)) {
            return;
        }
        if (containsAny(message, "全部装备", "不限装备类别", "不限品类")) {
            target.setEquipmentKeyword(null);
            return;
        }
        String keyword = EquipmentQueryNormalizer.normalize(message);
        if (keyword != null && !keyword.isBlank()) {
            target.setEquipmentKeyword(keyword);
        }
    }

    @Override
    public void cache(Long conversationId, AgentRequirement requirement) {
        redisTemplate.opsForValue().set(
                RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + conversationId,
                write(requirement),
                RedisTtl.withJitter(Duration.ofMinutes(Math.max(1, properties.getMemoryTtlMinutes())), 60));
    }

    @Override
    public void evict(Long conversationId) {
        redisTemplate.delete(RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + conversationId);
    }

    private void mergeSports(AgentRequirement target, AgentChatRequest request, String message) {
        List<String> explicit = request.getSportCodes() == null ? List.of() : request.getSportCodes().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(6)
                .toList();
        if (!explicit.isEmpty()) {
            target.setSportCodes(new ArrayList<>(explicit));
            return;
        }
        if (request.getSportCode() != null && !request.getSportCode().isBlank()) {
            target.setSportCodes(new ArrayList<>(List.of(request.getSportCode().trim())));
            return;
        }
        // 显式运动编码比 allSportsRequested 更可靠。即使旧版前端误把二者同时发送，
        // 后端也不会清空已经选择的羽毛球等条件。
        if (request.isAllSportsRequested() || containsAny(message, "不限球类", "全部球类", "所有球类")) {
            target.setSportCodes(new ArrayList<>());
            return;
        }
        List<String> inferred = inferSports(message);
        if (!inferred.isEmpty()) {
            target.setSportCodes(new ArrayList<>(inferred));
        }
    }

    private void mergeDateAndTime(AgentRequirement target, String message) {
        LocalDate date = parseDate(message);
        LocalTime[] range = parseTimeRange(message);
        if (date != null) {
            target.setTargetDate(date);
        }
        if (range != null) {
            target.setStartTime(range[0]);
            target.setEndTime(range[1]);
            if (target.getTargetDate() == null) {
                target.setTargetDate(LocalDate.now());
            }
            return;
        }
        if (containsAny(message, "今晚", "晚上", "晚间", "夜场", "下班后", "下班")) {
            target.setTargetDate(date == null ? LocalDate.now() : date);
            target.setStartTime(LocalTime.of(19, 0));
            target.setEndTime(LocalTime.of(20, 0));
        } else if (containsAny(message, "上午", "早上", "早场")) {
            target.setTargetDate(date == null ? LocalDate.now() : date);
            target.setStartTime(LocalTime.of(9, 0));
            target.setEndTime(LocalTime.of(10, 0));
        } else if (containsAny(message, "下午")) {
            target.setTargetDate(date == null ? LocalDate.now() : date);
            target.setStartTime(LocalTime.of(14, 0));
            target.setEndTime(LocalTime.of(15, 0));
        }
    }

    private void mergeBudget(AgentRequirement target, String message) {
        if (containsAny(message, "不限预算", "预算不限", "不考虑价格")) {
            target.setMaxBudget(null);
            return;
        }
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        BigDecimal value = null;
        while (matcher.find()) {
            value = new BigDecimal(matcher.group(1));
        }
        if (value != null) {
            target.setMaxBudget(value);
        }
    }

    private void mergeDistance(AgentRequirement target, String message) {
        if (containsAny(message, "不限距离", "距离不限")) {
            target.setMaxDistanceMeters(null);
            return;
        }
        Matcher matcher = DISTANCE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        int meters = unit.equals("米") || unit.equals("m") ? (int) value : (int) (value * 1000);
        target.setMaxDistanceMeters(Math.max(100, Math.min(meters, 50000)));
    }

    private void mergeLevel(AgentRequirement target, String message, LoginUser loginUser) {
        if (containsAny(message, "不限水平", "水平不限", "不限级别")) {
            target.setLevel("不限");
        } else if (containsAny(message, "新手", "入门", "初学")) {
            target.setLevel("初级");
        } else if (message.contains("中级")) {
            target.setLevel("中级");
        } else if (containsAny(message, "高级", "高手", "进阶")) {
            target.setLevel("高级");
        } else if (target.getLevel() == null || target.getLevel().isBlank()) {
            target.setLevel(loginUser == null || loginUser.getLevel() == null || loginUser.getLevel().isBlank()
                    ? "不限" : loginUser.getLevel());
        }
    }

    private Set<String> inferIntents(String message) {
        Set<String> intents = new LinkedHashSet<>();
        if (containsAny(message, "场所", "场馆", "球馆", "附近", "场地", "哪里", "团购", "私教")) {
            intents.add("PLACE");
        }
        if (containsAny(message, "约球", "活动", "加入", "搭子", "局")) {
            intents.add("ACTIVITY");
        }
        if (containsAny(message, "装备", "球拍", "球鞋", "护具", "球包", "买", "购买")) {
            intents.add("EQUIPMENT");
        }
        return intents;
    }

    private void mergeIntents(AgentRequirement target, Set<String> intents) {
        if (!intents.isEmpty()) {
            target.setIntents(new ArrayList<>(intents));
        }
    }

    /**
     * A venue budget and an equipment budget are different constraints. When a new turn
     * explicitly switches between those domains, an old amount must not silently filter
     * the new query unless the user says to keep the previous budget.
     */
    private void clearStaleCrossDomainBudget(AgentRequirement target,
                                             Set<String> currentIntents,
                                             String message) {
        Set<String> previousIntents = new LinkedHashSet<>(
                target.getIntents() == null ? List.of() : target.getIntents());
        if (previousIntents.isEmpty() || currentIntents.isEmpty()
                || !java.util.Collections.disjoint(previousIntents, currentIntents)) {
            return;
        }
        boolean switchesEquipmentDomain = previousIntents.contains("EQUIPMENT")
                != currentIntents.contains("EQUIPMENT");
        boolean keepPreviousBudget = containsAny(message,
                "同样预算", "相同预算", "这个预算", "刚才预算", "还是这个价格");
        if (switchesEquipmentDomain && !keepPreviousBudget) {
            target.setMaxBudget(null);
        }
    }

    private LocalDate parseDate(String message) {
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                return LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        Matcher monthDay = MONTH_DAY_PATTERN.matcher(message);
        if (monthDay.find()) {
            try {
                LocalDate candidate = LocalDate.of(LocalDate.now().getYear(),
                        Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
                return candidate.isBefore(LocalDate.now()) ? candidate.plusYears(1) : candidate;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (message.contains("后天")) return LocalDate.now().plusDays(2);
        if (message.contains("明天")) return LocalDate.now().plusDays(1);
        if (containsAny(message, "今天", "今晚")) return LocalDate.now();
        return null;
    }

    private LocalTime[] parseTimeRange(String message) {
        // 日期中的“07-16”不能被当成“07:00-16:00”，先移除日期片段再识别时段。
        String timeText = DATE_PATTERN.matcher(message).replaceAll(" ");
        timeText = MONTH_DAY_PATTERN.matcher(timeText).replaceAll(" ");
        Matcher range = TIME_RANGE_PATTERN.matcher(timeText);
        if (range.find()) {
            LocalTime start = time(message, range.group(1), range.group(2));
            LocalTime end = time(message, range.group(3), range.group(4));
            if (end.isAfter(start)) {
                return new LocalTime[]{start, end};
            }
        }
        Matcher single = SINGLE_TIME_PATTERN.matcher(timeText);
        if (single.find()) {
            String minute = single.group(2) == null ? single.group(3) : single.group(2);
            LocalTime start = time(message, single.group(1), minute);
            return new LocalTime[]{start, start.plusHours(1)};
        }
        return null;
    }

    private LocalTime time(String message, String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        if (hour < 12 && containsAny(message, "下午", "晚上", "晚间", "今晚", "夜场", "下班")) {
            hour += 12;
        }
        int minute = minuteText == null || minuteText.isBlank() ? 0 : Integer.parseInt(minuteText);
        return LocalTime.of(hour % 24, minute);
    }

    private List<String> inferSports(String message) {
        List<String> sports = new ArrayList<>();
        if (message.contains("羽毛")) sports.add("badminton");
        if (message.contains("乒乓")) sports.add("table_tennis");
        if (message.contains("足球")) sports.add("football");
        if (message.contains("篮球")) sports.add("basketball");
        if (message.contains("网球")) sports.add("tennis");
        if (message.contains("排球")) sports.add("volleyball");
        return sports;
    }

    private AgentRequirement copy(AgentRequirement source) {
        AgentRequirement target = new AgentRequirement();
        if (source == null) {
            return target;
        }
        target.setSportCodes(new ArrayList<>(source.getSportCodes() == null ? List.of() : source.getSportCodes()));
        target.setTargetDate(source.getTargetDate());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setMaxBudget(source.getMaxBudget());
        target.setMaxDistanceMeters(source.getMaxDistanceMeters());
        target.setLevel(source.getLevel());
        target.setEquipmentKeyword(source.getEquipmentKeyword());
        target.setIntents(new ArrayList<>(source.getIntents() == null ? List.of() : source.getIntents()));
        target.setLastSelectedCardIds(new ArrayList<>(source.getLastSelectedCardIds() == null ? List.of() : source.getLastSelectedCardIds()));
        return target;
    }

    private AgentRequirement read(String json) {
        if (json == null || json.isBlank()) {
            return new AgentRequirement();
        }
        try {
            return objectMapper.readValue(json, AgentRequirement.class);
        } catch (JsonProcessingException ignored) {
            return new AgentRequirement();
        }
    }

    private String write(AgentRequirement requirement) {
        try {
            return objectMapper.writeValueAsString(requirement);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize agent requirement", e);
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
