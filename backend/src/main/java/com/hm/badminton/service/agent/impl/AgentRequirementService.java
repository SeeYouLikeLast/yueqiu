package com.hm.badminton.service.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentCommand;
import com.hm.badminton.dto.agent.AgentCommandType;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentRequirementExtraction;
import com.hm.badminton.dto.agent.AgentTimePreset;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.service.agent.IAgentPersistenceService;
import com.hm.badminton.service.agent.IAgentRequirementExtractor;
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
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final Set<String> ALLOWED_SPORTS = Set.of(
            "badminton", "table_tennis", "football", "basketball", "tennis", "volleyball");
    private static final Set<String> ALLOWED_INTENTS = Set.of(
            "PLACE", "ACTIVITY", "EQUIPMENT", "BOOKING_RULES");
    private static final Set<String> ALLOWED_LEVELS = Set.of("不限", "初级", "中级", "高级");
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "BALANCED", "PRICE", "DISTANCE", "RATING", "TIME", "VALUE");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
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
    private final IAgentRequirementExtractor requirementExtractor;

    public AgentRequirementService(IAgentPersistenceService persistenceService,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   AgentProperties properties,
                                   IAgentRequirementExtractor requirementExtractor) {
        this.persistenceService = persistenceService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.requirementExtractor = requirementExtractor;
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

    /**
     * 将本轮自然语言合并到上一轮结构化需求中。
     *
     * <p>这里不拼接整段聊天文本，而是维护城市、球类、日期、时段、预算、
     * 距离、水平、偏好和业务意图等可直接查询的字段。用户只修改其中一个
     * 条件时，其余条件会继续沿用。</p>
     */
    @Override
    public AgentRequirement merge(AgentRequirement previous, AgentChatRequest request, LoginUser loginUser) {
        // 快捷按钮携带受控 Command。该分支完全不猜测按钮文案，校验结构化字段后直接进入 Graph。
        if (request.getCommand() != null && request.getCommand().getType() != null) {
            return mergeCommand(previous, request, loginUser);
        }

        // 1. 复制上一轮条件。本轮没有提到的字段默认继续沿用，避免追问后丢失上下文。
        AgentRequirement merged = copy(previous);
        String message = request.getMessage() == null ? "" : request.getMessage().trim();

        // 2. 只根据本轮文本初步识别业务对象：场所、约球活动、装备；一个问题可以命中多个。
        Set<String> ruleIntents = inferIntents(message);
        Set<String> previousIntents = new LinkedHashSet<>(safeList(merged.getIntents()));

        // 预约规则是独立的只读知识意图，不继承上一轮推荐条件，也不需要模型补全。
        if (ruleIntents.contains("BOOKING_RULES")) {
            return bookingRulesRequirement(merged, "rule");
        }

        // 3. “按距离重新筛”等话术只是在操作上一轮结果，不应重新猜测业务对象。
        // 只有用户没有明确说场所/装备/活动时，才继承上一轮意图。
        if ((isRangeExpansionFollowUp(message) || isContextRefinementFollowUp(message))
                && !mentionsExplicitBusinessDomain(message)
                && !previousIntents.isEmpty()) {
            ruleIntents = previousIntents;
        }

        // 4. 规则无法稳定识别、或一句话包含多个条件时，才让模型补充一个 JSON patch。
        // 模型字段先写入，后面的 request/rule 字段会覆盖它，因此明确规则始终优先。
        Optional<AgentRequirementExtraction> modelExtraction = shouldUseModelExtraction(message, ruleIntents)
                ? requirementExtractor.extract(message, previous)
                : Optional.empty();
        Set<String> modelIntents = modelExtraction
                .map(AgentRequirementExtraction::getIntents)
                .map(this::validIntents)
                .orElseGet(LinkedHashSet::new);
        Set<String> currentIntents = ruleIntents.isEmpty() ? modelIntents : ruleIntents;

        // 5. 用户明确从场所切到装备（或反向切换）时，旧预算通常不应跨业务复用。
        clearStaleCrossDomainBudget(merged, currentIntents, message);
        modelExtraction.ifPresent(extraction -> applyModelExtraction(merged, extraction));

        // 6. 按字段逐项覆盖结构化需求。每个 mergeXxx 只处理自己负责的维度。
        mergeCity(merged, request, message, loginUser);
        mergeSports(merged, request, message);
        // 只有规则本身明确命中装备域时才从原句提取类别；否则保留模型已校验的类别，
        // 避免把“帮我挑一个更适合我的”整句误归一化成商品关键词。
        mergeEquipmentKeyword(merged, ruleIntents, message);
        mergeDateAndTime(merged, message);
        mergeBudget(merged, message);
        mergeDistance(merged, message);
        mergeLevel(merged, message, loginUser);
        mergePreferences(merged, message);
        mergeSortPreference(merged, message);
        mergeAvailabilityAndRefund(merged, message);

        // 7. 规则识别到的意图覆盖模型；只有规则为空时才保留已校验的模型意图。
        if (!ruleIntents.isEmpty()) {
            mergeIntents(merged, ruleIntents);
        }
        return merged;
    }

    /**
     * Merges a quick action without parsing its display label.
     *
     * <p>The command controls only whitelisted recommendation dimensions. Database IDs,
     * mapper methods and write operations are intentionally absent from the DTO.</p>
     */
    private AgentRequirement mergeCommand(AgentRequirement previous,
                                          AgentChatRequest request,
                                          LoginUser loginUser) {
        AgentRequirement target = copy(previous);
        AgentCommand command = request.getCommand();

        // 1. Resolve the workflow from a backend enum. Refinement commands inherit the
        // previous domain; first-turn refinements use a conservative domain default.
        Set<String> intents = commandIntents(command.getType(), target.getIntents());
        clearStaleCrossDomainBudget(target, intents, "");
        target.setIntents(new ArrayList<>(intents));
        source(target, "intents", "command");
        if (!intents.contains("EQUIPMENT")) {
            target.setEquipmentKeyword(null);
            target.getFieldSources().remove("equipmentKeyword");
        }
        if (command.getType() == AgentCommandType.VIEW_BOOKING_RULES) {
            return bookingRulesRequirement(target, "command");
        }

        // 2. Location comes from the normal trusted request/profile path. Sport values from
        // the command are whitelisted and override the page's legacy sport fields.
        mergeCity(target, request, "", loginUser);
        List<String> commandSports = validSports(command.getSportCodes());
        if (!commandSports.isEmpty()) {
            target.setSportCodes(commandSports);
            source(target, "sportCodes", "command");
        } else if (command.isAllSportsRequested()) {
            target.setSportCodes(new ArrayList<>());
            source(target, "sportCodes", "command");
        } else {
            mergeSports(target, request, "");
        }

        // 3. Apply only validated filters. A null field keeps conversation memory while an
        // explicit clear flag removes a previous budget/category.
        applyCommandTime(target, command);
        if (command.isClearBudget()) {
            target.setMinBudget(null);
            target.setMaxBudget(null);
            source(target, "budget", "command");
        } else if (command.getMinBudget() != null || command.getMaxBudget() != null) {
            target.setMinBudget(validMoney(command.getMinBudget()));
            target.setMaxBudget(validMoney(command.getMaxBudget()));
            normalizeBudgetOrder(target);
            source(target, "budget", "command");
        }
        if (command.getMaxDistanceMeters() != null) {
            target.setMaxDistanceMeters(Math.max(100, Math.min(50000, command.getMaxDistanceMeters())));
            source(target, "maxDistanceMeters", "command");
        } else if (command.getType() == AgentCommandType.FILTER_DISTANCE) {
            target.setMaxDistanceMeters(target.getMaxDistanceMeters() == null
                    ? 5000 : Math.max(500, target.getMaxDistanceMeters()));
            target.setSortPreference("DISTANCE");
            source(target, "maxDistanceMeters", "command");
            source(target, "sortPreference", "command");
        }
        if (command.getLevel() != null && ALLOWED_LEVELS.contains(command.getLevel().trim())) {
            target.setLevel(command.getLevel().trim());
            source(target, "level", "command");
        } else if (target.getLevel() == null || target.getLevel().isBlank()) {
            target.setLevel(loginUser == null || loginUser.getLevel() == null
                    ? "不限" : loginUser.getLevel());
            source(target, "level", loginUser == null ? "default" : "profile");
        }
        if (command.isClearEquipmentKeyword()) {
            target.setEquipmentKeyword(null);
            source(target, "equipmentKeyword", "command");
        } else if (command.getEquipmentKeyword() != null && intents.contains("EQUIPMENT")) {
            String keyword = EquipmentQueryNormalizer.normalize(command.getEquipmentKeyword());
            if (keyword != null && !keyword.isBlank()) {
                target.setEquipmentKeyword(keyword);
                source(target, "equipmentKeyword", "command");
            }
        }
        String sort = normalizeSort(command.getSortPreference());
        if (sort != null) {
            target.setSortPreference(sort);
            source(target, "sortPreference", "command");
        } else if (command.getType() == AgentCommandType.FILTER_PRICE) {
            target.setSortPreference("VALUE");
            source(target, "sortPreference", "command");
        }
        mergeValidatedTags(target, command.getPreferenceTags(), command.getAvoidTags(), "command");
        if (command.getAvailabilityRequired() != null) {
            target.setAvailabilityRequired(command.getAvailabilityRequired());
            source(target, "availabilityRequired", "command");
        }
        if (command.getRefundableRequired() != null) {
            target.setRefundableRequired(command.getRefundableRequired());
            source(target, "refundableRequired", "command");
        }
        return target;
    }

    private void applyCommandTime(AgentRequirement target, AgentCommand command) {
        LocalDate date = command.getTargetDate();
        LocalTime start = command.getStartTime();
        LocalTime end = command.getEndTime();
        if (command.getTimePreset() != null) {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            LocalTime now = LocalTime.now(BUSINESS_ZONE);
            if (command.getTimePreset() == AgentTimePreset.TONIGHT) {
                date = now.isBefore(LocalTime.of(19, 0)) ? today : today.plusDays(1);
                start = LocalTime.of(19, 0);
                end = LocalTime.of(20, 0);
            } else if (command.getTimePreset() == AgentTimePreset.TODAY) {
                date = today;
            } else if (command.getTimePreset() == AgentTimePreset.TOMORROW) {
                date = today.plusDays(1);
            }
        }
        if (date != null && !date.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            target.setTargetDate(date);
            source(target, "targetDate", "command");
        }
        if (start != null) {
            target.setStartTime(start);
            target.setEndTime(end != null && end.isAfter(start) ? end : start.plusHours(1));
            target.setDurationMinutes((int) Duration.between(
                    target.getStartTime(), target.getEndTime()).toMinutes());
            source(target, "time", "command");
        } else if (command.getDurationMinutes() != null) {
            target.setDurationMinutes(Math.max(30, Math.min(720, command.getDurationMinutes())));
            source(target, "durationMinutes", "command");
        }
    }

    private Set<String> commandIntents(AgentCommandType type, List<String> previousIntents) {
        return switch (type) {
            case FIND_NEARBY_PLACES, VIEW_VENUE_PRODUCTS ->
                    new LinkedHashSet<>(List.of("PLACE"));
            case VIEW_BOOKING_RULES -> new LinkedHashSet<>(List.of("BOOKING_RULES"));
            case FIND_JOINABLE_ACTIVITIES -> new LinkedHashSet<>(List.of("ACTIVITY"));
            case RECOMMEND_EQUIPMENT -> new LinkedHashSet<>(List.of("EQUIPMENT"));
            case FILTER_DISTANCE -> inheritedOrDefault(previousIntents, "PLACE");
            case FILTER_BUDGET -> inheritedOrDefault(previousIntents, "EQUIPMENT");
            case FILTER_LEVEL -> inheritedOrDefault(previousIntents, "ACTIVITY");
            case FILTER_PRICE, REFINE_RESULTS -> inheritedOrDefault(previousIntents, "PLACE");
        };
    }

    private Set<String> inheritedOrDefault(List<String> previousIntents, String fallback) {
        Set<String> validated = validIntents(previousIntents);
        return validated.isEmpty() ? new LinkedHashSet<>(List.of(fallback)) : validated;
    }

    private boolean shouldUseModelExtraction(String message, Set<String> ruleIntents) {
        if (requirementExtractor == null || message == null || message.isBlank()) {
            return false;
        }
        if (ruleIntents.isEmpty()) {
            return true;
        }
        boolean complexSentence = message.length() >= Math.max(8, properties.getRequirementModelMinLength())
                && containsAny(message, "并且", "而且", "同时", "最好", "比较", "适合", "但是", "不要太", "帮我安排");
        boolean ambiguousReference = containsAny(message, "那个", "这种", "上一个", "刚才那个", "差不多的", "你看着选");
        boolean missingSport = inferSports(message).isEmpty()
                && (ruleIntents.contains("PLACE") || ruleIntents.contains("ACTIVITY") || ruleIntents.contains("EQUIPMENT"));
        return complexSentence || ambiguousReference || (message.length() >= 12 && missingSport);
    }

    /**
     * Applies model output as a lower-priority patch.
     *
     * <p>Every enum-like value is whitelisted, numeric values are clamped, invalid dates
     * are ignored, and deterministic rule parsing runs afterwards to overwrite this patch.</p>
     */
    private void applyModelExtraction(AgentRequirement target, AgentRequirementExtraction extraction) {
        Set<String> intents = validIntents(extraction.getIntents());
        if (!intents.isEmpty()) {
            target.setIntents(new ArrayList<>(intents));
            source(target, "intents", "model");
        }
        List<String> sports = validSports(extraction.getSportCodes());
        if (!sports.isEmpty()) {
            target.setSportCodes(sports);
            source(target, "sportCodes", "model");
        }
        String city = normalizeCity(extraction.getCity());
        if (city != null && city.length() <= 20) {
            target.setCity(city);
            source(target, "city", "model");
        }
        LocalDate date = parseIsoDate(extraction.getTargetDate());
        if (date != null && !date.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            target.setTargetDate(date);
            source(target, "targetDate", "model");
        }
        LocalTime start = parseIsoTime(extraction.getStartTime());
        LocalTime end = parseIsoTime(extraction.getEndTime());
        if (start != null) {
            target.setStartTime(start);
            target.setEndTime(end != null && end.isAfter(start) ? end : start.plusHours(1));
            target.setDurationMinutes((int) Duration.between(
                    target.getStartTime(), target.getEndTime()).toMinutes());
            source(target, "time", "model");
        } else if (extraction.getDurationMinutes() != null) {
            target.setDurationMinutes(Math.max(30, Math.min(720, extraction.getDurationMinutes())));
            source(target, "durationMinutes", "model");
        }
        if (extraction.getMinBudget() != null || extraction.getMaxBudget() != null) {
            target.setMinBudget(validMoney(extraction.getMinBudget()));
            target.setMaxBudget(validMoney(extraction.getMaxBudget()));
            normalizeBudgetOrder(target);
            source(target, "budget", "model");
        }
        if (extraction.getMaxDistanceMeters() != null) {
            target.setMaxDistanceMeters(Math.max(100, Math.min(50000, extraction.getMaxDistanceMeters())));
            source(target, "maxDistanceMeters", "model");
        }
        if (extraction.getLevel() != null && ALLOWED_LEVELS.contains(extraction.getLevel().trim())) {
            target.setLevel(extraction.getLevel().trim());
            source(target, "level", "model");
        }
        if (extraction.getEquipmentKeyword() != null && intents.contains("EQUIPMENT")) {
            String keyword = EquipmentQueryNormalizer.normalize(extraction.getEquipmentKeyword());
            if (keyword != null && !keyword.isBlank()) {
                target.setEquipmentKeyword(keyword);
                source(target, "equipmentKeyword", "model");
            }
        }
        String sort = normalizeSort(extraction.getSortPreference());
        if (sort != null) {
            target.setSortPreference(sort);
            source(target, "sortPreference", "model");
        }
        mergeValidatedTags(target, extraction.getPreferenceTags(), extraction.getAvoidTags(), "model");
        if (extraction.getAvailabilityRequired() != null) {
            target.setAvailabilityRequired(extraction.getAvailabilityRequired());
            source(target, "availabilityRequired", "model");
        }
        if (extraction.getRefundableRequired() != null) {
            target.setRefundableRequired(extraction.getRefundableRequired());
            source(target, "refundableRequired", "model");
        }
    }

    private Set<String> validIntents(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            if (value != null && ALLOWED_INTENTS.contains(value.trim().toUpperCase(Locale.ROOT))) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private List<String> validSports(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && ALLOWED_SPORTS.contains(value.trim()))
                .map(String::trim)
                .distinct()
                .limit(6)
                .toList();
    }

    private void mergeValidatedTags(AgentRequirement target,
                                    List<String> preferences,
                                    List<String> avoids,
                                    String valueSource) {
        List<String> preferred = validTags(preferences);
        List<String> avoided = validTags(avoids);
        if (!preferred.isEmpty()) {
            target.setPreferenceTags(mergeTags(target.getPreferenceTags(), preferred));
            source(target, "preferences", valueSource);
        }
        if (!avoided.isEmpty()) {
            target.setAvoidTags(mergeTags(target.getAvoidTags(), avoided));
            source(target, "preferences", valueSource);
        }
    }

    private List<String> validTags(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && !value.isBlank() && value.trim().length() <= 20)
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList();
    }

    private List<String> mergeTags(List<String> current, List<String> additions) {
        LinkedHashSet<String> values = new LinkedHashSet<>(safeList(current));
        values.addAll(additions);
        return values.stream().limit(10).toList();
    }

    private String normalizeSort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_SORTS.contains(normalized) ? normalized : null;
    }

    private BigDecimal validMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100000));
    }

    private void normalizeBudgetOrder(AgentRequirement target) {
        if (target.getMinBudget() != null && target.getMaxBudget() != null
                && target.getMinBudget().compareTo(target.getMaxBudget()) > 0) {
            BigDecimal minimum = target.getMaxBudget();
            target.setMaxBudget(target.getMinBudget());
            target.setMinBudget(minimum);
        }
    }

    private LocalDate parseIsoDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalTime parseIsoTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
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
        // 球类来源按可靠性排序：前端结构化多选 > 旧版单选字段 > “不限球类” > 文本规则识别。
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

    /**
     * 识别需要查询的业务对象。
     *
     * <p>当前是确定性规则，不让模型直接控制数据库工具。优点是快、可测试；
     * 局限是新说法需要补充规则，因此上下文筛选命令会在后面单独处理。</p>
     */
    private Set<String> inferIntents(String message) {
        Set<String> intents = new LinkedHashSet<>();
        // 规则查询必须先于“场馆/团购”等业务词判断，避免“场馆预约规则”
        // 同时落入 PLACE 分支并继续查询上一轮场所和商品。
        if (containsAny(message, "预约规则", "预订规则", "核销规则", "退款规则", "使用规则")) {
            intents.add("BOOKING_RULES");
            return intents;
        }
        // PLACE 同时覆盖真实场所及绑定到场所的团购/私教商品。
        if (containsAny(message, "场所", "场馆", "球馆", "附近", "场地", "哪里", "团购", "私教", "环境", "空场")) {
            intents.add("PLACE");
        }
        // ACTIVITY 只查询可以加入的约球活动，不包含场馆团购。
        if (containsAny(message, "约球", "活动", "加入", "搭子", "组局", "球局", "哪些局", "的局")) {
            intents.add("ACTIVITY");
        }
        // 只有显式装备词或购买动作才进入装备域。归一化器不能单独决定意图，
        // 否则“帮我挑一个更适合我的”这类模糊句会被整句误判为商品类别。
        if (containsAny(message,
                "装备", "球拍", "拍子", "球鞋", "运动鞋", "鞋子", "手胶",
                "护具", "球包", "球袜", "购买", "买一个", "买一")) {
            intents.add("EQUIPMENT");
        }
        // “今晚想打羽毛球”同时可能需要场所和可加入的局，因此并行查询两个分支。
        if (intents.isEmpty() && containsAny(message, "打球", "打羽毛球", "打乒乓球", "踢足球", "打篮球", "打网球", "打排球")) {
            intents.add("PLACE");
            intents.add("ACTIVITY");
        }
        return intents;
    }

    /**
     * Creates an isolated rules requirement and removes every recommendation constraint
     * inherited from the previous conversation turn.
     */
    private AgentRequirement bookingRulesRequirement(AgentRequirement target, String sourceName) {
        target.setSportCodes(new ArrayList<>());
        target.setTargetDate(null);
        target.setStartTime(null);
        target.setEndTime(null);
        target.setDurationMinutes(null);
        target.setMinBudget(null);
        target.setMaxBudget(null);
        target.setMaxDistanceMeters(null);
        target.setLevel("不限");
        target.setEquipmentKeyword(null);
        target.setPreferenceTags(new ArrayList<>());
        target.setAvoidTags(new ArrayList<>());
        target.setSortPreference("BALANCED");
        target.setAvailabilityRequired(false);
        target.setRefundableRequired(false);
        target.setLastSelectedCardIds(new ArrayList<>());
        target.setIntents(new ArrayList<>(List.of("BOOKING_RULES")));
        target.setFieldSources(new LinkedHashMap<>());
        source(target, "intents", sourceName);
        return target;
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
        // 这些词表达的是“如何处理上一轮候选结果”，本身不代表场所、活动或装备。
        return containsAny(message,
                "重新筛", "重新排序", "按距离", "按价格", "按评分", "按时间",
                "离我更近", "更近的", "更便宜", "最划算", "预约规则",
                "刚才的", "上面的");
    }

    private boolean mentionsExplicitBusinessDomain(String message) {
        // 用户显式说出新的业务对象时，以本轮为准，不再强制继承上一轮意图。
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
