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
import com.hm.badminton.utils.RedisTtl;
import com.hm.badminton.utils.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
            "venueName",
            "timePreference",
            "timeMatch",
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
            涉及场所、活动、装备时，必须只基于用户消息后 JSON 里的 cards 候选数据回答。
            装备推荐只能引用 cards 中已有的商品 title、price、subtitle、tags，不允许补充李宁、胜利、尤尼克斯、迪卡侬等候选里不存在的品牌或商品。
            场馆团购必须按用户的时间需求选择：问“今晚/晚上/下班后/19点/20点”时，优先推荐晚间或单场 1 小时；没有匹配时明确说明“当前候选未覆盖该时段，先给可预约的单场 1 小时”，不要说可以协商使用上午券。
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
        // 1. 开关保护：本地未配置 DashScope 时直接返回 503，而不是进入半可用状态。
        if (!agentProperties.isEnabled()) {
            throw new BusinessException(503, "AI 助手暂未开启");
        }
        // 2. 获取用户上下文。未登录用户也允许提问，用 userId=0 走 IP 限流和临时会话。
        LoginUser loginUser = userContext.current().orElse(null);
        Long userId = loginUser == null ? 0L : loginUser.getId();
        checkRateLimit(userId, clientIp);

        // 3. 创建或复用会话，并在写入本轮问题前读取历史用户偏好。
        //    这样“哪个团购最划算”会继承上一轮的运动、今晚/上午、预算等约束。
        AgentConversation conversation = ensureConversation(userId, request);
        String conversationContext = loadConversationContext(conversation.getId());
        saveMessage(conversation.getId(), userId, AgentConstants.ROLE_USER, request.getMessage(), null, null, null);

        // 4. 基于问题、定位、运动类型和预算调用业务工具，组装真实候选卡片。
        AgentContext context = buildContext(request, loginUser, conversationContext);
        ChatClient.Builder chatClientBuilder = safeChatClientBuilder();
        // 5. 有模型时让模型解释候选数据；模型不可用或调用失败时用本地摘要兜底。
        String answer = callModelOrFallback(request, context, chatClientBuilder);
        // 6. 持久化助手答案和卡片，更新会话时间，最后返回下一步可问的问题。
        saveMessage(conversation.getId(), userId, AgentConstants.ROLE_ASSISTANT, answer, context.cards(), null, null);
        touchConversation(conversation.getId());
        storeConversationContext(conversation.getId(), conversationContext, request.getMessage());

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
        redisTemplate.delete(RedisConstants.AGENT_CONVERSATION_CONTEXT_KEY + conversationId);
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

    private AgentContext buildContext(AgentChatRequest request, LoginUser loginUser, String conversationContext) {
        String message = request.getMessage();
        String effectiveMessage = message + (conversationContext.isBlank() ? "" : "\n历史偏好：" + conversationContext);
        // 1. 运动类型优先级：前端多选 > 前端当前运动 > 从自然语言里猜测。
        List<String> sportCodes = normalizeSports(request.getSportCodes(), request.getSportCode(),
                firstNotBlank(guessSport(message), guessSport(conversationContext)));
        List<String> querySports = sportCodes.isEmpty() ? List.of("") : sportCodes;
        // 2. 位置优先级：本次请求定位 > 登录用户最近定位 > 默认西安。
        String city = firstNotBlank(request.getCity(), loginUser == null ? null : loginUser.getCity(), "西安市");
        Double lng = request.getLng() != null ? request.getLng() : loginUser == null ? null : loginUser.getLongitude();
        Double lat = request.getLat() != null ? request.getLat() : loginUser == null ? null : loginUser.getLatitude();
        // 3. 从用户问题里提取预算和意图，用于减少无关工具调用。
        Integer budget = guessBudget(message);
        if (budget == null) {
            budget = guessBudget(conversationContext);
        }
        String timePreference = firstNotBlank(guessTimePreference(message), guessTimePreference(conversationContext));
        boolean wantsActivity = containsAny(effectiveMessage, "约球", "活动", "加入", "搭子", "局");
        boolean wantsEquipment = containsAny(effectiveMessage, "装备", "球拍", "球鞋", "护具", "球包", "购买", "买");
        boolean wantsPlace = containsAny(effectiveMessage, "场所", "场馆", "球馆", "附近", "场地", "哪里", "私教", "团购");
        boolean broad = !wantsActivity && !wantsEquipment && !wantsPlace;

        List<AgentCard> cards = new ArrayList<>();
        // 4. 多球类问题逐个查询，每种球类限制卡片数量，避免一次回答塞满屏幕。
        for (String sportCode : querySports) {
            List<AgentCard> places = List.of();
            if (wantsPlace || broad) {
                // 4.1 场所来自高德真实 POI；场馆团购来自本地数据库的 placeRank 槽位。
                places = placeTool.searchNearbyPlaces(sportCode, city, lng, lat, 8000, null);
                cards.addAll(places.stream().limit(2).toList());
                for (AgentCard place : places.stream().limit(2).toList()) {
                    Integer placeRank = placeRankOf(place);
                    if (placeRank != null) {
                        String placeSportCode = firstNotBlank(stringMeta(place, "sportCode"), sportCode);
                        List<AgentCard> venueProducts = venueProductTool.searchVenueProducts(placeSportCode, placeRank, null, budget, timePreference)
                                .stream()
                                .limit(1)
                                .toList();
                        venueProducts.forEach(product -> attachPlaceContext(product, place));
                        cards.addAll(venueProducts);
                    }
                }
            }
            if (wantsActivity || broad) {
                // 4.2 约球活动按城市、运动和用户水平过滤，并排除用户自己创建/加入的局。
                Long currentUserId = loginUser == null ? null : loginUser.getId();
                cards.addAll(activityTool.searchJoinableActivities(sportCode, city, loginUser == null ? null : loginUser.getLevel(), currentUserId)
                        .stream()
                        .limit(2)
                        .toList());
            }
            if (wantsEquipment || broad) {
                // 4.3 装备按运动、关键词和预算过滤；包含“便宜/秒杀”意图时追加限时抢购。
                cards.addAll(equipmentTool.searchEquipment(sportCode, cleanKeyword(effectiveMessage), budget).stream().limit(2).toList());
                if (containsAny(effectiveMessage, "秒杀", "特价", "抢购", "便宜")) {
                    cards.addAll(equipmentTool.searchSeckillEquipment(sportCode).stream().limit(2).toList());
                }
            }
        }
        // 5. 控制最大卡片数量，减少模型上下文和前端渲染压力。
        return new AgentContext(sportCodes, city, lng, lat, budget, timePreference, conversationContext,
                cards.stream().limit(18).toList());
    }

    private String callModelOrFallback(AgentChatRequest request, AgentContext context, ChatClient.Builder builder) {
        if (context.cards().isEmpty()) {
            return groundedAnswer(context);
        }
        if (requiresStrictGrounding(context)) {
            return groundedAnswer(context);
        }
        // 1. builder 为空通常表示未配置 DashScope，直接走本地兜底回答。
        if (builder == null) {
            return fallbackAnswer(context);
        }
        try {
            // 2. 只把适合模型理解的字段交给模型，内部 action/id/placeRank 保留在卡片 meta 中。
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userMessage", request.getMessage());
            payload.put("sportCodes", context.sportCodes());
            payload.put("city", context.city());
            payload.put("budget", context.budget());
            payload.put("timePreference", context.timePreference());
            payload.put("conversationContext", context.conversationContext());
            payload.put("cards", cardsForModel(context.cards()));
            payload.put("userPreference", userPreferenceTool.getCurrentUserPreference());
            String content = builder.build()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user("用户问题和已查询到的真实候选数据如下，请基于候选数据回答：\n" + writeJson(payload))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return groundedAnswer(context);
            }
            // 3. 防止模型把内部字段名复述给用户。
            String answer = sanitizeAnswer(content.trim());
            return answerLooksGrounded(answer, context.cards()) ? answer : groundedAnswer(context);
        } catch (Exception ex) {
            // 4. 第三方模型失败不影响业务卡片返回，前端仍可展示可点击结果。
            return groundedAnswer(context);
        }
    }

    private ChatClient.Builder safeChatClientBuilder() {
        try {
            return chatClientBuilderProvider.getIfAvailable();
        } catch (BeansException ex) {
            return null;
        }
    }

    private boolean requiresStrictGrounding(AgentContext context) {
        boolean hasEquipment = context.cards().stream()
                .anyMatch(card -> AgentConstants.CARD_EQUIPMENT.equals(card.getType())
                        || AgentConstants.CARD_SECKILL.equals(card.getType()));
        boolean hasTimedVenueProduct = !context.timePreference().isBlank()
                && context.cards().stream().anyMatch(card -> AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType()));
        return hasEquipment || hasTimedVenueProduct;
    }

    private String fallbackAnswer(AgentContext context) {
        return groundedAnswer(context);
    }

    private String groundedAnswer(AgentContext context) {
        if (context.cards().isEmpty()) {
            return "我暂时没有找到合适结果。你可以换个运动类型、预算或扩大附近范围再试一次。";
        }
        List<AgentCard> venueProducts = context.cards().stream()
                .filter(card -> AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType()))
                .limit(4)
                .toList();
        boolean hasPlace = context.cards().stream().anyMatch(card -> AgentConstants.CARD_PLACE.equals(card.getType()));
        if (!venueProducts.isEmpty() && (!context.timePreference().isBlank() || hasPlace)) {
            return groundedVenueProductAnswer(venueProducts, context.timePreference());
        }
        List<AgentCard> equipmentCards = context.cards().stream()
                .filter(card -> AgentConstants.CARD_EQUIPMENT.equals(card.getType()) || AgentConstants.CARD_SECKILL.equals(card.getType()))
                .limit(4)
                .toList();
        if (!equipmentCards.isEmpty()) {
            return groundedEquipmentAnswer(equipmentCards);
        }
        if (!venueProducts.isEmpty()) {
            return groundedVenueProductAnswer(venueProducts, context.timePreference());
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

    private String groundedEquipmentAnswer(List<AgentCard> cards) {
        StringBuilder answer = new StringBuilder("我只根据当前平台真实可购买装备为你筛选：\n\n");
        for (int i = 0; i < cards.size(); i++) {
            AgentCard card = cards.get(i);
            answer.append(i + 1)
                    .append(". ")
                    .append(card.getTitle())
                    .append(card.getPrice() == null ? "" : "（" + card.getPrice() + "）")
                    .append("：")
                    .append(firstNotBlank(card.getSubtitle(), "可点击商品卡片查看详情"));
            List<String> reasons = reasonTexts(card);
            if (!reasons.isEmpty()) {
                appendSentenceSeparator(answer);
                answer.append("推荐理由：").append(String.join("、", reasons));
            }
            appendSentenceSeparator(answer);
            answer.append("\n");
        }
        answer.append("\n需要看参数、加购或购买，请点击下方真实商品卡片。");
        return answer.toString();
    }

    private String groundedVenueProductAnswer(List<AgentCard> cards, String timePreference) {
        String timeText = "EVENING".equals(timePreference)
                ? "你提到的是晚间/今晚需求，我优先给你单场 1 小时或晚间可预约项目"
                : "MORNING".equals(timePreference)
                ? "你提到的是上午时段，我优先给你上午畅打或对应时段项目"
                : "我按可购买、价格和适合场景为你筛选了场馆团购";
        StringBuilder answer = new StringBuilder(timeText).append("：\n\n");
        for (int i = 0; i < cards.size(); i++) {
            AgentCard card = cards.get(i);
            String venueName = stringMeta(card, "venueName");
            answer.append(i + 1)
                    .append(". ")
                    .append(card.getTitle())
                    .append(card.getPrice() == null ? "" : "（" + card.getPrice() + "）")
                    .append("：")
                    .append(venueName.isBlank() ? "" : venueName + "，")
                    .append(firstNotBlank(card.getSubtitle(), "可点击团购卡片查看详情"));
            Object timeMatch = card.getMeta().get("timeMatch");
            if (timeMatch != null) {
                appendSentenceSeparator(answer);
                answer.append("时段匹配：").append(timeMatch);
            }
            appendSentenceSeparator(answer);
            answer.append("\n");
        }
        return answer.append("\n如果要下单，请点击对应团购卡片，前端会再让你确认。").toString();
    }

    private void appendSentenceSeparator(StringBuilder answer) {
        if (answer.isEmpty()) {
            return;
        }
        char last = answer.charAt(answer.length() - 1);
        if ("。.!！?？；;".indexOf(last) < 0) {
            answer.append("。");
        }
    }

    private List<String> reasonTexts(AgentCard card) {
        Object reasons = card.getMeta().get("recommendReasons");
        if (reasons instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(text -> !text.isBlank()).limit(3).toList();
        }
        Object pros = card.getMeta().get("pros");
        if (pros instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(text -> !text.isBlank()).limit(3).toList();
        }
        return List.of();
    }

    private boolean answerLooksGrounded(String answer, List<AgentCard> cards) {
        List<AgentCard> businessCards = cards.stream()
                .filter(card -> AgentConstants.CARD_EQUIPMENT.equals(card.getType())
                        || AgentConstants.CARD_SECKILL.equals(card.getType())
                        || AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType()))
                .toList();
        if (businessCards.isEmpty()) {
            return true;
        }
        return businessCards.stream().anyMatch(card -> containsTitleToken(answer, card.getTitle()));
    }

    private boolean containsTitleToken(String answer, String title) {
        if (answer == null || title == null || title.isBlank()) {
            return false;
        }
        if (answer.contains(title)) {
            return true;
        }
        String compactTitle = title.replaceAll("\\s+", "");
        String compactAnswer = answer.replaceAll("\\s+", "");
        if (compactAnswer.contains(compactTitle)) {
            return true;
        }
        for (String token : compactTitle.split("[·：:（）()\\-—]")) {
            if (token.length() >= 4 && compactAnswer.contains(token)) {
                return true;
            }
        }
        return false;
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

    /**
     * 优先读取 Redis 中的短期会话摘要；缓存缺失时回查 MySQL。
     * MySQL 是可恢复的事实来源，Redis 只是避免每次连续追问都扫历史消息。
     */
    private String loadConversationContext(Long conversationId) {
        String key = RedisConstants.AGENT_CONVERSATION_CONTEXT_KEY + conversationId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        int limit = Math.max(1, agentProperties.getMaxHistoryMessages());
        List<AgentMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getRole, AgentConstants.ROLE_USER)
                .orderByDesc(AgentMessage::getCreatedAt)
                .last("limit " + limit));
        Collections.reverse(messages);
        String context = messages.stream()
                .map(AgentMessage::getContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        if (!context.isBlank()) {
            redisTemplate.opsForValue().set(key, context, conversationMemoryTtl());
        }
        return context;
    }

    /**
     * 本轮问题进入会话短期记忆。只保留用户的需求文本，避免把模型自由文本再次当成事实来源。
     */
    private void storeConversationContext(Long conversationId, String previousContext, String currentMessage) {
        String current = currentMessage == null ? "" : currentMessage.trim();
        if (current.isBlank()) {
            return;
        }
        List<String> turns = new ArrayList<>();
        if (previousContext != null && !previousContext.isBlank()) {
            Collections.addAll(turns, previousContext.split("；"));
        }
        turns.add(current);
        int maxTurns = Math.max(1, agentProperties.getMaxHistoryMessages());
        String context = turns.stream()
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .skip(Math.max(0, turns.size() - maxTurns))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        redisTemplate.opsForValue().set(
                RedisConstants.AGENT_CONVERSATION_CONTEXT_KEY + conversationId,
                context,
                conversationMemoryTtl());
    }

    private Duration conversationMemoryTtl() {
        return RedisTtl.withJitter(
                Duration.ofMinutes(Math.max(1, agentProperties.getMemoryTtlMinutes())),
                60);
    }

    private void touchConversation(Long conversationId) {
        conversationMapper.updateById(new AgentConversation()
                .setId(conversationId)
                .setUpdatedAt(LocalDateTime.now()));
    }

    private void checkRateLimit(Long userId, String clientIp) {
        // 登录用户按 userId 限流，游客按 IP 限流；窗口时间由 Redis key TTL 控制。
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

    private void attachPlaceContext(AgentCard product, AgentCard place) {
        if (product == null || place == null) {
            return;
        }
        Map<String, Object> meta = product.getMeta();
        meta.put("venueName", place.getTitle());
        meta.put("placeTitle", place.getTitle());
        meta.put("placeSubtitle", place.getSubtitle());
        meta.put("city", place.getMeta().get("city"));
        meta.put("placeSource", "amap");
        if (place.getAction() != null) {
            meta.put("placeId", place.getAction().getId());
        }
    }

    private String stringMeta(AgentCard card, String key) {
        Object value = card.getMeta().get(key);
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if ("venueName".equals(key) && text.matches("\\d+")) {
            return "";
        }
        return text;
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
                // “不限球类”是筛选条件而不是装备名称；保留它会变成 SQL like 关键词，导致全量查询为空。
                .replaceAll("(推荐|帮我|找|购买|买|装备|不限球类|不限|球类|以内|以下|左右|元|块|附近|有没有|可以|适合|新手)", " ")
                .replaceAll("(羽毛球|羽毛|乒乓球|乒乓|足球|篮球|网球|排球)", " ")
                .replaceAll("[（）()，,。.!！?？、]", " ")
                .replaceAll("\\d+", " ")
                .trim();
        return text.isBlank() || text.length() > 12 ? null : text;
    }

    private String guessTimePreference(String message) {
        String text = message == null ? "" : message;
        if (containsAny(text, "上午", "早场", "早上", "08:00", "09:00", "10:00", "11:00", "上午8点", "上午9点", "上午10点", "上午11点")) {
            return "MORNING";
        }
        if (containsAny(text, "今晚", "晚上", "晚间", "夜场", "下班", "19点", "20点", "晚上7点", "晚上8点", "19:00", "20:00")) {
            return "EVENING";
        }
        if (containsAny(text, "下午", "14点", "15点", "16点", "17点", "14:00", "15:00", "16:00", "17:00")) {
            return "AFTERNOON";
        }
        return "";
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
                                String timePreference,
                                String conversationContext,
                                List<AgentCard> cards) {
    }
}
