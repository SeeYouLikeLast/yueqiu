package com.hm.badminton.service.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.service.agent.IAgentPersistenceService;
import com.hm.badminton.service.agent.IAgentRequirementService;
import com.hm.badminton.service.agent.tools.EquipmentQueryNormalizer;
import com.hm.badminton.utils.RedisTtl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts mergeable, typed constraints from conversational Chinese.
 *
 * <p>Every turn starts from the previous requirement and only replaces fields explicitly
 * mentioned in the new message. This preserves follow-ups such as “换成 50 元以内的”, while
 * source markers explain whether a value came from the request, a rule or the user profile.</p>
 */
@Service
public class AgentRequirementService implements IAgentRequirementService {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(20\\d{2})\\s*(?:[-/.]|年)\\s*(\\d{1,2})\\s*(?:[-/.]|月)\\s*(\\d{1,2})(?:日|号)?");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile(
            "(?<![\\d:：])(1[0-2]|0?[1-9])\\s*(?:月|[-/.])\\s*"
                    + "(3[01]|[12]\\d|0?[1-9])(?:日|号)?(?![\\d:：])");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(?<!\\d)(2[0-3]|[01]?\\d)(?:[:：点时]([0-5]?\\d)?)?\\s*(?:-|到|至|~|～)\\s*"
                    + "(2[0-3]|[01]?\\d)(?:[:：点时]([0-5]?\\d)?)?(?!\\d)");
    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile(
            "(?<!\\d)(2[0-3]|[01]?\\d)(?:(?:[:：]([0-5]\\d))|(?:点|时)(?:([0-5]?\\d)分?)?)(?!\\d)");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d{1,3})\\s*(小时|分钟)");
    private static final Pattern BUDGET_RANGE_PATTERN = Pattern.compile(
            "(?:预算|价格)?\\s*(\\d{1,5})\\s*(?:-|到|至|~|～)\\s*(\\d{1,5})\\s*(?:元|块)?");
    private static final Pattern BUDGET_CONTEXT_PATTERN = Pattern.compile(
            "(?:预算(?:是|为|控制在)?|价格(?:在)?|最多|不超过|低于|控制在)\\s*(\\d{1,5})\\s*(?:元|块)?");
    private static final Pattern BUDGET_UNIT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,5})\\s*(?:元|块)\\s*(?:以内|以下|之内|左右)?");
    private static final Pattern BUDGET_MIN_PREFIX_PATTERN = Pattern.compile(
            "(?:至少|不低于|最低)\\s*(\\d{1,5})\\s*(?:元|块)?");
    private static final Pattern BUDGET_MIN_SUFFIX_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,5})\\s*(?:元|块)\\s*(?:以上|起步|起)(?!\\d)");
    private static final Pattern DISTANCE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(公里|千米|km|米|m)(?:以内|以下|之内)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITY_PATTERN = Pattern.compile("([\\p{IsHan}]{2,8}市)");

    private static final Map<String, String> SPORT_NAMES = Map.of(
            "羽毛球", "badminton",
            "乒乓球", "table_tennis",
            "足球", "football",
            "篮球", "basketball",
            "网球", "tennis",
            "排球", "volleyball");
    private static final List<String> KNOWN_CITIES = List.of("西安", "上海", "北京", "成都");
    private static final Map<String, String> PREFERENCE_ALIASES = Map.ofEntries(
            Map.entry("停车", "停车"),
            Map.entry("淋浴", "淋浴"),
            Map.entry("洗澡", "淋浴"),
            Map.entry("地铁", "近地铁"),
            Map.entry("交通方便", "交通方便"),
            Map.entry("灯光", "灯光好"),
            Map.entry("地胶", "专业地胶"),
            Map.entry("木地板", "木地板"),
            Map.entry("空调", "空调"),
            Map.entry("环境好", "环境好"),
            Map.entry("干净", "干净"),
            Map.entry("安静", "安静"),
            Map.entry("亲子", "亲子"),
            Map.entry("新手", "新手友好"),
            Map.entry("入门", "新手友好"),
            Map.entry("下班", "下班快打"));

    private final IAgentPersistenceService persistenceService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentRequirementService(IAgentPersistenceService persistenceService,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   AgentProperties properties) {
        this.persistenceService = persistenceService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AgentRequirement load(AgentTurnContext turn) {
        String key = RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + turn.conversationId();
        String json = redisTemplate == null ? null : redisTemplate.opsForValue().get(key);
        if ((json == null || json.isBlank()) && persistenceService != null) {
            json = persistenceService.requirementsJson(turn);
        }
        AgentRequirement requirement = read(json);
        if (requirement.getTargetDate() != null && requirement.getTargetDate().isBefore(LocalDate.now())) {
            requirement.setTargetDate(null);
            requirement.setStartTime(null);
            requirement.setEndTime(null);
            requirement.setDurationMinutes(null);
        }
        return requirement;
    }

    @Override
    public AgentRequirement merge(AgentRequirement previous, AgentChatRequest request, LoginUser loginUser) {
        AgentRequirement merged = copy(previous);
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        Set<String> currentIntents = inferIntents(message);
        Set<String> previousIntents = new LinkedHashSet<>(safeList(merged.getIntents()));
        if ((isRangeExpansionFollowUp(message) || isContextRefinementFollowUp(message))
                && !mentionsExplicitBusinessDomain(message)
                && !previousIntents.isEmpty()) {
            currentIntents = previousIntents;
        }

        clearStaleCrossDomainBudget(merged, currentIntents, message);
        mergeCity(merged, request, message, loginUser);
        mergeSports(merged, request, message);
        mergeEquipmentKeyword(merged, currentIntents, message);
        mergeDateAndTime(merged, message);
        mergeBudget(merged, message);
        mergeDistance(merged, message);
        mergeLevel(merged, message, loginUser);
        mergePreferences(merged, message);
        mergeSortPreference(merged, message);
        mergeAvailabilityAndRefund(merged, message);
        mergeIntents(merged, currentIntents);
        return merged;
    }

    @Override
    public void cache(AgentTurnContext turn, AgentRequirement requirement) {
        if (redisTemplate == null) {
            return;
        }
        int minutes = turn.anonymous()
                ? Math.max(1, properties.getAnonymousHistoryTtlDays()) * 24 * 60
                : Math.max(1, properties.getMemoryTtlMinutes());
        redisTemplate.opsForValue().set(
                RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + turn.conversationId(),
                write(requirement),
                RedisTtl.withJitter(Duration.ofMinutes(minutes), 60));
    }

    @Override
    public void evict(Long conversationId) {
        if (redisTemplate != null) {
            redisTemplate.delete(RedisConstants.AGENT_CONVERSATION_REQUIREMENT_KEY + conversationId);
        }
    }

    private void mergeCity(AgentRequirement target,
                           AgentChatRequest request,
                           String message,
                           LoginUser loginUser) {
        String city = normalizeCity(request.getCity());
        String source = "request";
        if (city == null) {
            city = parseCity(message);
            source = "rule";
        }
        if (city == null && (target.getCity() == null || target.getCity().isBlank()) && loginUser != null) {
            city = normalizeCity(loginUser.getCity());
            source = "profile";
        }
        if (city != null) {
            target.setCity(city);
            source(target, "city", source);
        }
    }

    private void mergeSports(AgentRequirement target, AgentChatRequest request, String message) {
        List<String> explicit = safeList(request.getSportCodes()).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(6)
                .toList();
        if (!explicit.isEmpty()) {
            target.setSportCodes(new ArrayList<>(explicit));
            source(target, "sportCodes", "request");
            return;
        }
        if (request.getSportCode() != null && !request.getSportCode().isBlank()) {
            target.setSportCodes(new ArrayList<>(List.of(request.getSportCode().trim())));
            source(target, "sportCodes", "request");
            return;
        }
        if (request.isAllSportsRequested() || containsAny(message, "不限球类", "全部球类", "所有球类")) {
            target.setSportCodes(new ArrayList<>());
            source(target, "sportCodes", "request");
            return;
        }
        List<String> inferred = inferSports(message);
        if (!inferred.isEmpty()) {
            target.setSportCodes(new ArrayList<>(inferred));
            source(target, "sportCodes", "rule");
        }
    }

    private void mergeEquipmentKeyword(AgentRequirement target,
                                       Set<String> currentIntents,
                                       String message) {
        if (!currentIntents.contains("EQUIPMENT") || isRangeExpansionFollowUp(message)) {
            return;
        }
        if (containsAny(message, "全部装备", "不限装备类别", "不限品类")) {
            target.setEquipmentKeyword(null);
            source(target, "equipmentKeyword", "request");
            return;
        }
        String keyword = EquipmentQueryNormalizer.normalize(message);
        if (keyword != null && !keyword.isBlank()) {
            target.setEquipmentKeyword(keyword);
            source(target, "equipmentKeyword", "rule");
        }
    }

    private void mergeDateAndTime(AgentRequirement target, String message) {
        LocalDate date = parseDate(message);
        LocalTime[] range = parseTimeRange(message);
        Integer duration = parseDuration(message);
        if (date != null) {
            target.setTargetDate(date);
            source(target, "targetDate", "rule");
        }
        if (range != null) {
            target.setStartTime(range[0]);
            target.setEndTime(range[1]);
            target.setDurationMinutes((int) Duration.between(range[0], range[1]).toMinutes());
            if (target.getTargetDate() == null) {
                target.setTargetDate(LocalDate.now());
            }
            source(target, "time", "rule");
            return;
        }
        if (duration != null) {
            target.setDurationMinutes(duration);
            if (target.getStartTime() != null) {
                target.setEndTime(target.getStartTime().plusMinutes(duration));
            }
            source(target, "durationMinutes", "rule");
        }
        if (containsAny(message, "今晚", "明晚", "晚上", "晚间", "夜场", "下班后", "下班")) {
            if (target.getTargetDate() == null || date != null) {
                target.setTargetDate(date == null ? LocalDate.now() : date);
            }
            target.setStartTime(LocalTime.of(19, 0));
            target.setEndTime(LocalTime.of(20, 0));
            target.setDurationMinutes(60);
            source(target, "time", "rule");
        } else if (containsAny(message, "上午", "早上", "早场")) {
            target.setTargetDate(date == null ? LocalDate.now() : date);
            target.setStartTime(LocalTime.of(9, 0));
            target.setEndTime(LocalTime.of(10, 0));
            target.setDurationMinutes(60);
            source(target, "time", "rule");
        } else if (message.contains("下午")) {
            target.setTargetDate(date == null ? LocalDate.now() : date);
            target.setStartTime(LocalTime.of(14, 0));
            target.setEndTime(LocalTime.of(15, 0));
            target.setDurationMinutes(60);
            source(target, "time", "rule");
        }
    }

    private void mergeBudget(AgentRequirement target, String message) {
        if (containsAny(message, "不限预算", "预算不限", "不考虑价格")) {
            target.setMinBudget(null);
            target.setMaxBudget(null);
            source(target, "budget", "request");
            return;
        }
        Matcher range = BUDGET_RANGE_PATTERN.matcher(withoutDateAndTime(message));
        if (range.find()) {
            BigDecimal first = new BigDecimal(range.group(1));
            BigDecimal second = new BigDecimal(range.group(2));
            target.setMinBudget(first.min(second));
            target.setMaxBudget(first.max(second));
            source(target, "budget", "rule");
            return;
        }
        BigDecimal minimum = lastNumber(BUDGET_MIN_PREFIX_PATTERN, message);
        if (minimum == null) {
            minimum = lastNumber(BUDGET_MIN_SUFFIX_PATTERN, message);
        }
        if (minimum != null) {
            target.setMinBudget(minimum);
            if (target.getMaxBudget() != null && target.getMaxBudget().compareTo(minimum) < 0) {
                target.setMaxBudget(null);
            }
            source(target, "budget", "rule");
            return;
        }
        BigDecimal value = lastNumber(BUDGET_CONTEXT_PATTERN, message);
        if (value == null) {
            value = lastNumber(BUDGET_UNIT_PATTERN, withoutDateAndTime(message));
        }
        if (value != null) {
            target.setMinBudget(null);
            target.setMaxBudget(value);
            source(target, "budget", "rule");
        }
    }

    private void mergeDistance(AgentRequirement target, String message) {
        if (containsAny(message, "不限距离", "距离不限")) {
            target.setMaxDistanceMeters(null);
            source(target, "maxDistanceMeters", "request");
            return;
        }
        Matcher matcher = DISTANCE_PATTERN.matcher(message);
        if (!matcher.find()) {
            if (isRangeExpansionFollowUp(message)) {
                int expanded = target.getMaxDistanceMeters() == null
                        ? 10000
                        : Math.min(50000, Math.max(1000, target.getMaxDistanceMeters() * 2));
                target.setMaxDistanceMeters(expanded);
                source(target, "maxDistanceMeters", "follow-up");
            }
            return;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        int meters = unit.equals("米") || unit.equals("m") ? (int) value : (int) (value * 1000);
        target.setMaxDistanceMeters(Math.max(100, Math.min(meters, 50000)));
        source(target, "maxDistanceMeters", "rule");
    }

    private void mergeLevel(AgentRequirement target, String message, LoginUser loginUser) {
        if (containsAny(message, "不限水平", "水平不限", "不限级别")) {
            target.setLevel("不限");
            source(target, "level", "request");
        } else if (containsAny(message, "新手", "入门", "初学", "零基础")) {
            target.setLevel("初级");
            source(target, "level", "rule");
        } else if (message.contains("中级")) {
            target.setLevel("中级");
            source(target, "level", "rule");
        } else if (containsAny(message, "高级", "高手", "进阶")) {
            target.setLevel("高级");
            source(target, "level", "rule");
        } else if (target.getLevel() == null || target.getLevel().isBlank()) {
            target.setLevel(loginUser == null || loginUser.getLevel() == null || loginUser.getLevel().isBlank()
                    ? "不限" : loginUser.getLevel());
            source(target, "level", loginUser == null ? "default" : "profile");
        }
    }

    private void mergePreferences(AgentRequirement target, String message) {
        LinkedHashSet<String> preferred = new LinkedHashSet<>(safeList(target.getPreferenceTags()));
        LinkedHashSet<String> avoided = new LinkedHashSet<>(safeList(target.getAvoidTags()));
        for (Map.Entry<String, String> entry : PREFERENCE_ALIASES.entrySet()) {
            if (!message.contains(entry.getKey())) {
                continue;
            }
            if (containsNegationNear(message, entry.getKey())) {
                avoided.add(entry.getValue());
                preferred.remove(entry.getValue());
            } else {
                preferred.add(entry.getValue());
                avoided.remove(entry.getValue());
            }
        }
        if (containsAny(message, "避开拥挤", "不要拥挤", "人少一点")) {
            preferred.add("不拥挤");
        }
        if (containsAny(message, "别太远", "不要太远")) {
            preferred.add("距离近");
        }
        if (containsAny(message, "别太贵", "不要太贵")) {
            preferred.add("价格友好");
        }
        target.setPreferenceTags(new ArrayList<>(preferred));
        target.setAvoidTags(new ArrayList<>(avoided));
        if (!preferred.isEmpty() || !avoided.isEmpty()) {
            source(target, "preferences", "rule");
        }
    }

    private void mergeSortPreference(AgentRequirement target, String message) {
        String preference = null;
        if (containsAny(message, "离我最近", "最近的", "按距离", "更近")) {
            preference = "DISTANCE";
        } else if (containsAny(message, "最便宜", "价格最低", "低价", "省钱")) {
            preference = "PRICE";
        } else if (containsAny(message, "评分最高", "口碑最好", "评价最好")) {
            preference = "RATING";
        } else if (containsAny(message, "今晚有空", "时段优先", "时间最合适")) {
            preference = "TIME";
        } else if (containsAny(message, "性价比", "最划算", "划算")) {
            preference = "VALUE";
        }
        if (preference != null) {
            target.setSortPreference(preference);
            source(target, "sortPreference", "rule");
        }
    }

    private void mergeAvailabilityAndRefund(AgentRequirement target, String message) {
        if (containsAny(message, "可以预订", "能预订", "有空场", "能加入", "可购买", "有现货")) {
            target.setAvailabilityRequired(true);
            source(target, "availabilityRequired", "rule");
        }
        if (containsAny(message, "可退", "能退款", "支持退款")) {
            target.setRefundableRequired(true);
            source(target, "refundableRequired", "rule");
        } else if (containsAny(message, "不用可退", "不要求退款")) {
            target.setRefundableRequired(false);
            source(target, "refundableRequired", "request");
        }
    }

    private Set<String> inferIntents(String message) {
        Set<String> intents = new LinkedHashSet<>();
        if (containsAny(message, "场所", "场馆", "球馆", "附近", "场地", "哪里", "团购", "私教", "环境", "空场")) {
            intents.add("PLACE");
        }
        if (containsAny(message, "约球", "活动", "加入", "搭子", "组局", "球局", "哪些局", "的局")) {
            intents.add("ACTIVITY");
        }
        String equipmentKeyword = EquipmentQueryNormalizer.normalize(message);
        if (equipmentKeyword != null || containsAny(message, "装备", "球拍", "球鞋", "护具", "球包", "购买", "买一个", "买一")) {
            intents.add("EQUIPMENT");
        }
        if (intents.isEmpty() && containsAny(message, "打球", "打羽毛球", "打乒乓球", "踢足球", "打篮球", "打网球", "打排球")) {
            intents.add("PLACE");
            intents.add("ACTIVITY");
        }
        return intents;
    }

    private void mergeIntents(AgentRequirement target, Set<String> intents) {
        if (!intents.isEmpty()) {
            target.setIntents(new ArrayList<>(intents));
            source(target, "intents", "rule");
        }
    }

    private void clearStaleCrossDomainBudget(AgentRequirement target,
                                             Set<String> currentIntents,
                                             String message) {
        Set<String> previousIntents = new LinkedHashSet<>(safeList(target.getIntents()));
        if (previousIntents.isEmpty() || currentIntents.isEmpty()
                || !java.util.Collections.disjoint(previousIntents, currentIntents)) {
            return;
        }
        boolean switchesEquipmentDomain = previousIntents.contains("EQUIPMENT")
                != currentIntents.contains("EQUIPMENT");
        boolean keepPreviousBudget = containsAny(message,
                "同样预算", "相同预算", "这个预算", "刚才预算", "还是这个价格");
        if (switchesEquipmentDomain && !keepPreviousBudget) {
            target.setMinBudget(null);
            target.setMaxBudget(null);
            target.getFieldSources().remove("budget");
        }
    }

    private LocalDate parseDate(String message) {
        LocalDate today = LocalDate.now();
        if (message.contains("后天")) return today.plusDays(2);
        if (containsAny(message, "明天", "明晚")) return today.plusDays(1);
        if (containsAny(message, "今天", "今晚")) return today;
        if (message.contains("下周末")) {
            return today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).plusWeeks(1);
        }
        if (message.contains("周末")) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        }
        LocalDate weekday = parseWeekday(message, today);
        if (weekday != null) {
            return weekday;
        }
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            return safeDate(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        }
        Matcher monthDay = MONTH_DAY_PATTERN.matcher(message);
        if (monthDay.find()) {
            LocalDate candidate = safeDate(today.getYear(),
                    Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
            return candidate != null && candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }
        return null;
    }

    private LocalDate parseWeekday(String message, LocalDate today) {
        Matcher matcher = Pattern.compile("(下周|本周|这周|周)([一二三四五六日天])").matcher(message);
        if (!matcher.find()) {
            return null;
        }
        DayOfWeek target = switch (matcher.group(2)) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
        if ("下周".equals(matcher.group(1))) {
            return today.with(TemporalAdjusters.next(target));
        }
        return today.with(TemporalAdjusters.nextOrSame(target));
    }

    private LocalTime[] parseTimeRange(String message) {
        String timeText = withoutDate(message);
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

    private Integer parseDuration(String message) {
        Matcher matcher = DURATION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(1));
        int minutes = "小时".equals(matcher.group(2)) ? value * 60 : value;
        return Math.max(30, Math.min(minutes, 12 * 60));
    }

    private LocalTime time(String message, String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        if (hour < 12 && containsAny(message, "下午", "晚上", "晚间", "今晚", "明晚", "夜场", "下班")) {
            hour += 12;
        }
        int minute = minuteText == null || minuteText.isBlank() ? 0 : Integer.parseInt(minuteText);
        return LocalTime.of(hour % 24, minute);
    }

    private List<String> inferSports(String message) {
        List<String> sports = new ArrayList<>();
        for (Map.Entry<String, String> entry : SPORT_NAMES.entrySet()) {
            if (message.contains(entry.getKey())) {
                sports.add(entry.getValue());
            }
        }
        return sports.stream().distinct().toList();
    }

    private String parseCity(String message) {
        for (String city : KNOWN_CITIES) {
            if (message.contains(city)) {
                return city + (city.endsWith("市") ? "" : "市");
            }
        }
        Matcher matcher = CITY_PATTERN.matcher(message);
        return matcher.find() ? normalizeCity(matcher.group(1)) : null;
    }

    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String value = city.trim();
        return value.endsWith("市") ? value : value + "市";
    }

    private boolean isRangeExpansionFollowUp(String message) {
        return containsAny(message, "扩大附近范围", "扩大范围", "范围再大", "找远一点", "更远一点");
    }

    private boolean isContextRefinementFollowUp(String message) {
        return containsAny(message,
                "重新筛", "重新排序", "按距离", "按价格", "按评分", "按时间",
                "离我更近", "更近的", "更便宜", "最划算", "预约规则",
                "刚才的", "上面的");
    }

    private boolean mentionsExplicitBusinessDomain(String message) {
        return containsAny(message,
                "场所", "场馆", "球馆", "场地", "团购", "私教",
                "约球", "活动", "搭子", "组局", "球局",
                "装备", "球拍", "球鞋", "护具", "球包", "手胶");
    }

    private boolean containsNegationNear(String message, String keyword) {
        int index = message.indexOf(keyword);
        if (index < 0) {
            return false;
        }
        String prefix = message.substring(Math.max(0, index - 4), index);
        return containsAny(prefix, "不要", "不想", "无需", "没有", "避开");
    }

    private String withoutDateAndTime(String message) {
        return TIME_RANGE_PATTERN.matcher(withoutDate(message)).replaceAll(" ");
    }

    private String withoutDate(String message) {
        return MONTH_DAY_PATTERN.matcher(DATE_PATTERN.matcher(message).replaceAll(" ")).replaceAll(" ");
    }

    private BigDecimal lastNumber(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        BigDecimal value = null;
        while (matcher.find()) {
            value = new BigDecimal(matcher.group(1));
        }
        return value;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private AgentRequirement copy(AgentRequirement source) {
        AgentRequirement target = new AgentRequirement();
        if (source == null) {
            return target;
        }
        target.setSportCodes(new ArrayList<>(safeList(source.getSportCodes())));
        target.setCity(source.getCity());
        target.setTargetDate(source.getTargetDate());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setMinBudget(source.getMinBudget());
        target.setMaxBudget(source.getMaxBudget());
        target.setMaxDistanceMeters(source.getMaxDistanceMeters());
        target.setLevel(source.getLevel());
        target.setEquipmentKeyword(source.getEquipmentKeyword());
        target.setPreferenceTags(new ArrayList<>(safeList(source.getPreferenceTags())));
        target.setAvoidTags(new ArrayList<>(safeList(source.getAvoidTags())));
        target.setSortPreference(source.getSortPreference());
        target.setAvailabilityRequired(source.isAvailabilityRequired());
        target.setRefundableRequired(source.isRefundableRequired());
        target.setFieldSources(new LinkedHashMap<>(source.getFieldSources() == null ? Map.of() : source.getFieldSources()));
        target.setIntents(new ArrayList<>(safeList(source.getIntents())));
        target.setLastSelectedCardIds(new ArrayList<>(safeList(source.getLastSelectedCardIds())));
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

    private void source(AgentRequirement target, String field, String source) {
        if (target.getFieldSources() == null) {
            target.setFieldSources(new LinkedHashMap<>());
        }
        target.getFieldSources().put(field, source);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
