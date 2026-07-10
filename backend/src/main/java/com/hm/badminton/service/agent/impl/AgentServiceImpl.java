package com.hm.badminton.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentChatResponse;
import com.hm.badminton.dto.agent.AgentConversationView;
import com.hm.badminton.dto.agent.AgentMessageView;
import com.hm.badminton.entity.AgentConversation;
import com.hm.badminton.entity.AgentMessage;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.mapper.agent.AgentMessageMapper;
import com.hm.badminton.service.agent.IAgentService;
import com.hm.badminton.service.agent.tools.ActivityAgentTool;
import com.hm.badminton.service.agent.tools.EquipmentAgentTool;
import com.hm.badminton.service.agent.tools.PlaceAgentTool;
import com.hm.badminton.service.agent.tools.UserPreferenceAgentTool;
import com.hm.badminton.service.agent.tools.VenueProductAgentTool;
import com.hm.badminton.utils.IdGenerator;
import com.hm.badminton.utils.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentServiceImpl implements IAgentService {

    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d{2,5})\\s*(元|块|以内|以下|左右)?");
    private static final List<String> MODEL_META_KEYS = List.of(
            "distanceMeters",
            "rating",
            "reviewCount",
            "available",
            "stock",
            "sold",
            "suitableLevel",
            "sceneTags",
            "pros",
            "cons",
            "recommendScore",
            "recommendReasons"
    );
    private static final String SYSTEM_PROMPT = """
            你是“约个球”平台的 AI 助手。
            你的任务是帮助用户选择运动场所、查找可加入的约球活动、推荐可购买装备。
            你不能编造场所、价格、库存、活动人数。
            涉及场所、活动、装备时，必须基于系统提供的真实候选数据回答。
            下单、加入活动、加入购物车、抢购、支付必须由用户点击确认，不能自动替用户完成。
            不要向用户展示任何内部字段名，例如 placeRank、id、payload、meta、action。
            回答要适合手机端展示，但要给出选择依据，例如距离、价格、可购买性、运动类型匹配、适合人群。
            优先给出 2 到 4 个选择，每个选择用一句话说明为什么推荐。
            """;

    private final AgentProperties agentProperties;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final PlaceAgentTool placeTool;
    private final VenueProductAgentTool venueProductTool;
    private final ActivityAgentTool activityTool;
    private final EquipmentAgentTool equipmentTool;
    private final UserPreferenceAgentTool userPreferenceTool;
    private final UserContext userContext;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public AgentServiceImpl(AgentProperties agentProperties,
                            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                            AgentConversationMapper conversationMapper,
                            AgentMessageMapper messageMapper,
                            PlaceAgentTool placeTool,
                            VenueProductAgentTool venueProductTool,
                            ActivityAgentTool activityTool,
                            EquipmentAgentTool equipmentTool,
                            UserPreferenceAgentTool userPreferenceTool,
                            UserContext userContext,
                            IdGenerator idGenerator,
                            ObjectMapper objectMapper,
                            StringRedisTemplate redisTemplate) {
        this.agentProperties = agentProperties;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.placeTool = placeTool;
        this.venueProductTool = venueProductTool;
        this.activityTool = activityTool;
        this.equipmentTool = equipmentTool;
        this.userPreferenceTool = userPreferenceTool;
        this.userContext = userContext;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean aiEnabled() {
        return safeChatClientBuilder() != null;
    }

    @Override
    @Transactional
    public AgentChatResponse chat(AgentChatRequest request, String clientIp) {
        if (!agentProperties.isEnabled()) {
            throw new BusinessException(503, "AI 助手暂未开启");
        }
        LoginUser loginUser = userContext.current().orElse(null);
        Long userId = loginUser == null ? 0L : loginUser.getId();
        checkRateLimit(userId, clientIp);

        AgentConversation conversation = ensureConversation(userId, request);
        saveMessage(conversation.getId(), userId, AgentConstants.ROLE_USER, request.getMessage(), null, null, null);

        AgentContext context = buildContext(request, loginUser);
        ChatClient.Builder chatClientBuilder = safeChatClientBuilder();
        String answer = callModelOrFallback(request, context, chatClientBuilder);
        saveMessage(conversation.getId(), userId, AgentConstants.ROLE_ASSISTANT, answer, context.cards(), null, null);
        touchConversation(conversation.getId());

        return new AgentChatResponse(
                conversation.getId(),
                answer,
                context.cards(),
                quickReplies(context),
                chatClientBuilder != null);
    }

    @Override
    public List<AgentConversationView> conversations(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                        .eq(AgentConversation::getUserId, userId)
                        .eq(AgentConversation::getStatus, 1)
                        .orderByDesc(AgentConversation::getUpdatedAt)
                        .last("limit 30"))
                .stream()
                .map(this::toConversationView)
                .toList();
    }

    @Override
    public List<AgentMessageView> messages(Long userId, Long conversationId) {
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "会话不存在");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .orderByAsc(AgentMessage::getCreatedAt))
                .stream()
                .map(this::toMessageView)
                .toList();
    }

    @Override
    public void deleteConversation(Long userId, Long conversationId) {
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "会话不存在");
        }
        conversationMapper.updateById(new AgentConversation()
                .setId(conversationId)
                .setStatus(0)
                .setUpdatedAt(LocalDateTime.now()));
    }

    private AgentConversation ensureConversation(Long userId, AgentChatRequest request) {
        if (request.getConversationId() != null) {
            AgentConversation existing = conversationMapper.selectById(request.getConversationId());
            if (existing != null && Objects.equals(existing.getUserId(), userId) && Integer.valueOf(1).equals(existing.getStatus())) {
                return existing;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = new AgentConversation()
                .setId(idGenerator.nextId())
                .setUserId(userId)
                .setTitle(titleOf(request.getMessage()))
                .setStatus(1)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private AgentContext buildContext(AgentChatRequest request, LoginUser loginUser) {
        String message = request.getMessage();
        List<String> sportCodes = normalizeSports(request.getSportCodes(), request.getSportCode(), guessSport(message));
        List<String> querySports = sportCodes.isEmpty() ? List.of("") : sportCodes;
        String city = firstNotBlank(request.getCity(), loginUser == null ? null : loginUser.getCity(), "西安市");
        Double lng = request.getLng() != null ? request.getLng() : loginUser == null ? null : loginUser.getLongitude();
        Double lat = request.getLat() != null ? request.getLat() : loginUser == null ? null : loginUser.getLatitude();
        Integer budget = guessBudget(message);
        boolean wantsActivity = containsAny(message, "约球", "活动", "加入", "搭子", "局");
        boolean wantsEquipment = containsAny(message, "装备", "球拍", "球鞋", "护具", "球包", "购买", "买");
        boolean wantsPlace = containsAny(message, "场所", "场馆", "球馆", "附近", "场地", "哪里", "私教", "团购");
        boolean broad = !wantsActivity && !wantsEquipment && !wantsPlace;

        List<AgentCard> cards = new ArrayList<>();
        for (String sportCode : querySports) {
            List<AgentCard> places = List.of();
            if (wantsPlace || broad) {
                places = placeTool.searchNearbyPlaces(sportCode, city, lng, lat, 8000, null);
                cards.addAll(places.stream().limit(2).toList());
                for (AgentCard place : places.stream().limit(2).toList()) {
                    Integer placeRank = placeRankOf(place);
                    if (placeRank != null) {
                        String placeSportCode = firstNotBlank(stringMeta(place, "sportCode"), sportCode);
                        cards.addAll(venueProductTool.searchVenueProducts(placeSportCode, placeRank, null, budget).stream().limit(1).toList());
                    }
                }
            }
            if (wantsActivity || broad) {
                Long currentUserId = loginUser == null ? null : loginUser.getId();
                cards.addAll(activityTool.searchJoinableActivities(sportCode, city, loginUser == null ? null : loginUser.getLevel(), currentUserId)
                        .stream()
                        .limit(2)
                        .toList());
            }
            if (wantsEquipment || broad) {
                cards.addAll(equipmentTool.searchEquipment(sportCode, cleanKeyword(message), budget).stream().limit(2).toList());
                if (containsAny(message, "秒杀", "特价", "抢购", "便宜")) {
                    cards.addAll(equipmentTool.searchSeckillEquipment(sportCode).stream().limit(2).toList());
                }
            }
        }
        return new AgentContext(sportCodes, city, lng, lat, budget, cards.stream().limit(18).toList());
    }

    private String callModelOrFallback(AgentChatRequest request, AgentContext context, ChatClient.Builder builder) {
        if (builder == null) {
            return fallbackAnswer(context);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userMessage", request.getMessage());
            payload.put("sportCodes", context.sportCodes());
            payload.put("city", context.city());
            payload.put("budget", context.budget());
            payload.put("cards", cardsForModel(context.cards()));
            payload.put("userPreference", userPreferenceTool.getCurrentUserPreference());
            String content = builder.build()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .tools(placeTool, venueProductTool, activityTool, equipmentTool, userPreferenceTool)
                    .user("用户问题和已查询到的真实候选数据如下，请基于候选数据回答：\n" + writeJson(payload))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return fallbackAnswer(context);
            }
            return sanitizeAnswer(content.trim());
        } catch (Exception ex) {
            return fallbackAnswer(context);
        }
    }

    private ChatClient.Builder safeChatClientBuilder() {
        try {
            return chatClientBuilderProvider.getIfAvailable();
        } catch (BeansException ex) {
            return null;
        }
    }

    private String fallbackAnswer(AgentContext context) {
        if (context.cards().isEmpty()) {
            return "我暂时没有找到合适结果。你可以换个运动类型、预算或扩大附近范围再试一次。";
        }
        long placeCount = context.cards().stream().filter(card -> AgentConstants.CARD_PLACE.equals(card.getType())).count();
        long activityCount = context.cards().stream().filter(card -> AgentConstants.CARD_ACTIVITY.equals(card.getType())).count();
        long venueProductCount = context.cards().stream().filter(card -> AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType())).count();
        long equipmentCount = context.cards().stream().filter(card -> AgentConstants.CARD_EQUIPMENT.equals(card.getType())).count();
        long seckillCount = context.cards().stream().filter(card -> AgentConstants.CARD_SECKILL.equals(card.getType())).count();
        List<String> parts = new ArrayList<>();
        if (placeCount > 0) {
            parts.add(placeCount + " 个附近场所");
        }
        if (venueProductCount > 0) {
            parts.add(venueProductCount + " 个场馆团购");
        }
        if (activityCount > 0) {
            parts.add(activityCount + " 个可加入约球");
        }
        if (equipmentCount > 0) {
            parts.add(equipmentCount + " 个装备选择");
        }
        if (seckillCount > 0) {
            parts.add(seckillCount + " 个限时抢购");
        }
        return "我根据你的需求找到了 " + String.join("、", parts) + "。下面的卡片可以直接查看详情或继续操作。";
    }

    private List<String> quickReplies(AgentContext context) {
        List<String> replies = new ArrayList<>();
        if (hasCardType(context, AgentConstants.CARD_PLACE)) {
            addQuickReply(replies, "看看这些场所的团购");
            addQuickReply(replies, "帮我按距离重新筛");
        }
        if (hasCardType(context, AgentConstants.CARD_VENUE_PRODUCT)) {
            addQuickReply(replies, "哪一个团购最划算");
            addQuickReply(replies, "帮我看预约规则");
        }
        if (hasCardType(context, AgentConstants.CARD_ACTIVITY)) {
            addQuickReply(replies, "哪些局现在能加入");
            addQuickReply(replies, "帮我选适合新手的局");
        }
        if (hasCardType(context, AgentConstants.CARD_EQUIPMENT) || hasCardType(context, AgentConstants.CARD_SECKILL)) {
            addQuickReply(replies, "帮我按预算筛装备");
            addQuickReply(replies, "这几件适合新手吗");
        }
        if (replies.isEmpty()) {
            addQuickReply(replies, "换个运动类型试试");
            addQuickReply(replies, "帮我扩大附近范围");
            addQuickReply(replies, "推荐新手装备");
            addQuickReply(replies, "帮我找能加入的局");
        } else if (!context.sportCodes().isEmpty()) {
            addQuickReply(replies, "换成离我更近的");
        }
        return replies.stream().limit(4).toList();
    }

    private boolean hasCardType(AgentContext context, String cardType) {
        return context.cards().stream().anyMatch(card -> cardType.equals(card.getType()));
    }

    private void addQuickReply(List<String> replies, String text) {
        if (!replies.contains(text)) {
            replies.add(text);
        }
    }

    private List<Map<String, Object>> cardsForModel(List<AgentCard> cards) {
        return cards.stream()
                .map(card -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", card.getType());
                    data.put("title", card.getTitle());
                    data.put("subtitle", card.getSubtitle());
                    data.put("price", card.getPrice());
                    data.put("tags", card.getTags());
                    if (card.getMeta() != null) {
                        for (String key : MODEL_META_KEYS) {
                            Object value = card.getMeta().get(key);
                            if (value != null) {
                                data.put(key, value);
                            }
                        }
                    }
                    return data;
                })
                .toList();
    }

    private String sanitizeAnswer(String answer) {
        return answer
                .replaceAll("(?i)\\s*[（(]?\\s*placeRank\\s*=\\s*\\d+\\s*[）)]?\\s*[：:，,]?\\s*", "")
                .replaceAll("(?i)placeRank\\s*=\\s*\\d+\\s*[：:，,]?\\s*", "")
                .replaceAll("(?i)\\s*\\(\\s*placeRank\\s*\\)", "")
                .trim();
    }

    private void saveMessage(Long conversationId,
                             Long userId,
                             String role,
                             String content,
                             List<AgentCard> cards,
                             String toolName,
                             Object toolResult) {
        LocalDateTime now = LocalDateTime.now();
        AgentMessage message = new AgentMessage()
                .setId(idGenerator.nextId())
                .setConversationId(conversationId)
                .setUserId(userId)
                .setRole(role)
                .setContent(content)
                .setCardsJson(cards == null || cards.isEmpty() ? null : writeJson(cards))
                .setToolName(toolName)
                .setToolResultJson(toolResult == null ? null : writeJson(toolResult))
                .setCreatedAt(now);
        messageMapper.insert(message);
    }

    private void touchConversation(Long conversationId) {
        conversationMapper.updateById(new AgentConversation()
                .setId(conversationId)
                .setUpdatedAt(LocalDateTime.now()));
    }

    private void checkRateLimit(Long userId, String clientIp) {
        String key = userId != null && userId > 0
                ? RedisConstants.AGENT_RATE_USER_KEY + userId
                : RedisConstants.AGENT_RATE_IP_KEY + (clientIp == null ? "unknown" : clientIp);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RedisConstants.AGENT_RATE_TTL);
        }
        if (count != null && count > agentProperties.getRateLimitPerMinute()) {
            throw new BusinessException(429, "AI 助手访问太频繁，请稍后再试");
        }
    }

    private AgentConversationView toConversationView(AgentConversation conversation) {
        AgentConversationView view = new AgentConversationView();
        view.setId(conversation.getId());
        view.setTitle(conversation.getTitle());
        view.setCreatedAt(conversation.getCreatedAt());
        view.setUpdatedAt(conversation.getUpdatedAt());
        return view;
    }

    private AgentMessageView toMessageView(AgentMessage message) {
        AgentMessageView view = new AgentMessageView();
        view.setId(message.getId());
        view.setRole(message.getRole());
        view.setContent(message.getContent());
        view.setCards(readCards(message.getCardsJson()));
        view.setCreatedAt(message.getCreatedAt());
        return view;
    }

    private List<AgentCard> readCards(String cardsJson) {
        if (cardsJson == null || cardsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cardsJson, new TypeReference<List<AgentCard>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI 助手数据序列化失败");
        }
    }

    private String titleOf(String message) {
        String text = message == null ? "新会话" : message.trim();
        if (text.length() <= 18) {
            return text.isBlank() ? "新会话" : text;
        }
        return text.substring(0, 18) + "...";
    }

    private Integer placeRankOf(AgentCard place) {
        Object value = place.getMeta().get("placeRank");
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringMeta(AgentCard card, String key) {
        Object value = card.getMeta().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeSport(String sportCode) {
        return sportCode == null ? "" : sportCode.trim();
    }

    private List<String> normalizeSports(List<String> sportCodes, String sportCode, String guessedSport) {
        List<String> values = new ArrayList<>();
        if (sportCodes != null) {
            sportCodes.stream()
                    .map(this::normalizeSport)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .limit(6)
                    .forEach(values::add);
        }
        if (values.isEmpty()) {
            String fallback = normalizeSport(firstNotBlank(sportCode, guessedSport));
            if (!fallback.isBlank()) {
                values.add(fallback);
            }
        }
        return values;
    }

    private String guessSport(String message) {
        String text = message == null ? "" : message;
        if (text.contains("乒乓") || text.contains("乒乓球")) return "table_tennis";
        if (text.contains("足球")) return "football";
        if (text.contains("篮球")) return "basketball";
        if (text.contains("网球")) return "tennis";
        if (text.contains("排球")) return "volleyball";
        if (text.contains("羽毛") || text.contains("羽毛球")) return "badminton";
        return "";
    }

    private Integer guessBudget(String message) {
        Matcher matcher = BUDGET_PATTERN.matcher(message == null ? "" : message);
        Integer best = null;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value >= 20) {
                best = value;
            }
        }
        return best;
    }

    private String cleanKeyword(String message) {
        if (message == null) {
            return null;
        }
        String text = message
                .replaceAll("(推荐|帮我|找|购买|买|装备|以内|以下|左右|元|块|附近|有没有|可以|适合|新手)", " ")
                .replaceAll("\\d+", " ")
                .trim();
        return text.length() > 12 ? null : text;
    }

    private boolean containsAny(String message, String... keywords) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record AgentContext(List<String> sportCodes,
                                String city,
                                Double lng,
                                Double lat,
                                Integer budget,
                                List<AgentCard> cards) {
    }
}
