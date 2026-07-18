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
import com.hm.badminton.dto.agent.AgentModelDecision;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.entity.AgentConversation;
import com.hm.badminton.entity.AgentMessage;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.mapper.agent.AgentMessageMapper;
import com.hm.badminton.service.agent.IAgentService;
import com.hm.badminton.service.agent.IAgentPersistenceService;
import com.hm.badminton.service.agent.IAgentRequirementService;
import com.hm.badminton.service.agent.graph.AgentGraphNodeHandler;
import com.hm.badminton.service.agent.graph.AgentGraphProgress;
import com.hm.badminton.service.agent.graph.AgentGraphRunContext;
import com.hm.badminton.service.agent.graph.AgentGraphState;
import com.hm.badminton.service.agent.graph.AgentGraphWorkflow;
import com.hm.badminton.service.agent.graph.AgentPlaceBranchResult;
import com.hm.badminton.service.agent.tools.ActivityAgentTool;
import com.hm.badminton.service.agent.tools.EquipmentAgentTool;
import com.hm.badminton.service.agent.tools.PlaceAgentTool;
import com.hm.badminton.service.agent.tools.VenueProductAgentTool;
import com.hm.badminton.utils.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AI 助手编排服务。
 *
 * <p>大模型不直接访问数据库。本类先识别运动、位置、预算和时间意图，再调用场所、团购、活动、
 * 装备工具取得真实候选卡片，最后让 DashScope 仅基于候选数据生成解释；模型不可用时使用本地
 * 摘要降级。会话消息落 MySQL，短期偏好存 Redis，购买和加入活动仍需前端二次确认。</p>
 */
@Service
public class AgentServiceImpl implements IAgentService, AgentGraphNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);
    private static final int MIN_PLACE_RECOMMENDATIONS = 2;
    private static final String RUN_TURN = "turn";
    private static final String RUN_REQUIREMENT = "requirement";
    private static final String RUN_CONTEXT = "recommendationContext";
    private static final String RUN_DECISION = "decision";
    private static final String RUN_MODEL_ENABLED = "modelEnabled";
    private static final List<String> MODEL_META_KEYS = List.of(
            "distanceMeters",
            "rating",
            "reviewCount",
            "available",
            "venueName",
            "timePreference",
            "timeMatch",
            "serviceDate",
            "startTime",
            "endTime",
            "suitableLevel",
            "sceneTags",
            "pros",
            "cons",
            "recommendScore",
            "recommendReasons"
    );
    private static final String SYSTEM_PROMPT = """
            你是“约个球”平台的候选选择器，不负责创造业务事实。
            你只能从 cards 中选择 1 到 4 个 cardId，禁止返回不存在的 cardId。
            场所类问题有多个候选时应选择 2 到 3 个，优先选择带有真实可售团购的场所。
            优先考虑用户的运动、日期、时段、预算、距离和水平要求。
            recommendationReasons 必须为每个 selectedCardId 分别生成一句有差异的推荐理由。
            理由只能使用该卡片中已有的时间、价格、距离、水平、场景、优缺点等事实，不能补充未提供的信息。
            只返回一个 JSON 对象，不要使用 Markdown，不要输出 JSON 以外的内容：
            {"selectedCardIds":["cardId"],"recommendationReasons":{"cardId":"该卡片的具体推荐理由"},"explanation":"本次选择的总体依据"}
            explanation 只概括总体选择依据；页面上的逐项理由来自 recommendationReasons。
            """;

    private final AgentProperties agentProperties;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final PlaceAgentTool placeTool;
    private final VenueProductAgentTool venueProductTool;
    private final ActivityAgentTool activityTool;
    private final EquipmentAgentTool equipmentTool;
    private final UserContext userContext;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final IAgentPersistenceService persistenceService;
    private final IAgentRequirementService requirementService;
    private final AgentGraphWorkflow agentGraphWorkflow;

    public AgentServiceImpl(AgentProperties agentProperties,
                            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                            AgentConversationMapper conversationMapper,
                            AgentMessageMapper messageMapper,
                            PlaceAgentTool placeTool,
                            VenueProductAgentTool venueProductTool,
                            ActivityAgentTool activityTool,
                            EquipmentAgentTool equipmentTool,
                            UserContext userContext,
                            ObjectMapper objectMapper,
                            StringRedisTemplate redisTemplate,
                            IAgentPersistenceService persistenceService,
                            IAgentRequirementService requirementService,
                            AgentGraphWorkflow agentGraphWorkflow) {
        this.agentProperties = agentProperties;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.placeTool = placeTool;
        this.venueProductTool = venueProductTool;
        this.activityTool = activityTool;
        this.equipmentTool = equipmentTool;
        this.userContext = userContext;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.persistenceService = persistenceService;
        this.requirementService = requirementService;
        this.agentGraphWorkflow = agentGraphWorkflow;
    }

    @Override
    public boolean aiEnabled() {
        return safeChatClientBuilder() != null;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request, String clientIp) {
        return executeChat(request, clientIp, userContext.current().orElse(null), progress -> {
        });
    }

    @Override
    public SseEmitter chatStream(AgentChatRequest request, String clientIp) {
        LoginUser loginUser = userContext.current().orElse(null);
        SseEmitter emitter = new SseEmitter(120_000L);
        Thread.startVirtualThread(() -> {
            try {
                AgentChatResponse response = executeChat(request, clientIp, loginUser,
                        progress -> sendEvent(emitter, progress.event(), progress.data()));
                sendEvent(emitter, "done", response);
                emitter.complete();
            } catch (BusinessException ex) {
                sendEvent(emitter, "error", Map.of("code", ex.code(), "message", ex.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.error("Agent SSE request failed", ex);
                sendEvent(emitter, "error", Map.of("code", 500, "message", "AI 助手请求失败，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    private AgentChatResponse executeChat(AgentChatRequest request,
                                          String clientIp,
                                          LoginUser loginUser,
                                          Consumer<AgentGraphProgress> progress) {
        return agentGraphWorkflow.execute(this,
                new AgentGraphRunContext(request, clientIp, loginUser, progress));
    }

    /** First graph node: protect the endpoint and persist the user's question in a short transaction. */
    @Override
    public void beginTurn(AgentGraphRunContext run) {
        if (!agentProperties.isEnabled()) {
            throw new BusinessException(503, "AI 助手暂未开启");
        }
        Long userId = run.getLoginUser() == null ? 0L : run.getLoginUser().getId();
        checkRateLimit(userId, run.getClientIp());
        run.emit(AgentGraphProgress.stage("理解需求", "正在理解你的运动、时间和预算要求"));
        AgentTurnContext turn = timed(run.getRequestId(), "persist-question",
                () -> persistenceService.beginTurn(userId, run.getRequest()));
        run.put(RUN_TURN, turn);
        run.put(AgentGraphState.CONVERSATION_ID, turn.conversationId());
        run.emit(new AgentGraphProgress("conversation", Map.of("conversationId", turn.conversationId())));
    }

    /** Second graph node: merge the current question into structured cross-turn memory. */
    @Override
    public void understandRequirement(AgentGraphRunContext run) {
        AgentTurnContext turn = run.require(RUN_TURN, AgentTurnContext.class);
        AgentRequirement previous = timed(run.getRequestId(), "memory-load",
                () -> requirementService.load(turn.conversationId()));
        AgentRequirement requirement = timed(run.getRequestId(), "requirement-merge",
                () -> requirementService.merge(previous, run.getRequest(), run.getLoginUser()));
        run.put(RUN_REQUIREMENT, requirement);
        run.put(RUN_CONTEXT, baseContext(run.getRequest(), run.getLoginUser(), requirement));
    }

    /** Emits the stages for the branches that the graph is about to run. */
    @Override
    public void dispatchTools(AgentGraphRunContext run) {
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        if (shouldQuery(context, "PLACE")) {
            run.emit(AgentGraphProgress.stage("查询场所", "正在查询附近场所和真实可售时段"));
        }
        if (shouldQuery(context, "ACTIVITY")) {
            run.emit(AgentGraphProgress.stage("查询约球", "正在查找可以加入的约球活动"));
        }
        if (shouldQuery(context, "EQUIPMENT")) {
            run.emit(AgentGraphProgress.stage("查询装备", "正在按运动、预算和水平筛选装备"));
        }
    }

    /** Place branch: real AMap places must be known before products can be bound by place rank. */
    @Override
    public AgentPlaceBranchResult queryPlacesAndProducts(AgentGraphRunContext run) {
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        if (!shouldQuery(context, "PLACE")) {
            return AgentPlaceBranchResult.empty();
        }
        List<AgentCard> places = new ArrayList<>();
        List<AgentCard> products = new ArrayList<>();
        for (String sportCode : querySports(context)) {
            int radius = context.requirement().getMaxDistanceMeters() == null
                    ? 8000 : context.requirement().getMaxDistanceMeters();
            List<AgentCard> found = safeTool(run.getRequestId(), "places:" + sportCode,
                    () -> placeTool.searchNearbyPlaces(sportCode, context.city(), context.lng(), context.lat(), radius, null));
            List<AgentCard> visiblePlaces = found.stream().limit(2).toList();
            places.addAll(visiblePlaces);
            products.addAll(safeTool(run.getRequestId(), "venue-products:" + sportCode,
                    () -> venueProducts(visiblePlaces, sportCode, context.requirement())));
        }
        return new AgentPlaceBranchResult(places, products);
    }

    /** Activity branch, executed in parallel with place and equipment branches by StateGraph. */
    @Override
    public List<AgentCard> queryActivities(AgentGraphRunContext run) {
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        if (!shouldQuery(context, "ACTIVITY")) {
            return List.of();
        }
        Long userId = run.getLoginUser() == null ? null : run.getLoginUser().getId();
        List<AgentCard> result = new ArrayList<>();
        for (String sportCode : querySports(context)) {
            result.addAll(safeTool(run.getRequestId(), "activities:" + sportCode,
                    () -> activityTool.searchJoinableActivities(sportCode, context.city(),
                            context.requirement().getLevel(), userId).stream().limit(2).toList()));
        }
        return result;
    }

    /** Equipment branch, including seckill rows only when the user explicitly asks for discounts. */
    @Override
    public List<AgentCard> queryEquipment(AgentGraphRunContext run) {
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        if (!shouldQuery(context, "EQUIPMENT")) {
            return List.of();
        }
        String message = run.getRequest().getMessage();
        List<AgentCard> result = new ArrayList<>();
        for (String sportCode : querySports(context)) {
            result.addAll(safeTool(run.getRequestId(), "equipment:" + sportCode,
                    () -> equipmentTool.searchEquipment(sportCode, cleanKeyword(message), context.budget())
                            .stream().limit(2).toList()));
            if (containsAny(message, "秒杀", "特价", "抢购", "便宜")) {
                result.addAll(safeTool(run.getRequestId(), "seckill-equipment:" + sportCode,
                        () -> equipmentTool.searchSeckillEquipment(sportCode).stream().limit(2).toList()));
            }
        }
        return result;
    }

    /** Merge node: normalize all parallel outputs into the single candidate set sent to the model. */
    @Override
    public void mergeCandidates(AgentGraphRunContext run,
                                List<AgentCard> places,
                                List<AgentCard> venueProducts,
                                List<AgentCard> activities,
                                List<AgentCard> equipment) {
        AgentContext base = run.require(RUN_CONTEXT, AgentContext.class);
        List<AgentCard> cards = new ArrayList<>();
        cards.addAll(places);
        cards.addAll(venueProducts);
        cards.addAll(activities);
        cards.addAll(equipment);
        assignCardIds(cards);
        AgentContext context = base.withCards(cards.stream().limit(18).toList());
        run.put(RUN_CONTEXT, context);
        run.emit(new AgentGraphProgress("cards", context.cards()));
    }

    /** Model node: choose existing card ids and attach validated, card-specific AI reasons. */
    @Override
    public void selectCandidates(AgentGraphRunContext run) {
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        ChatClient.Builder chatClientBuilder = safeChatClientBuilder();
        run.emit(AgentGraphProgress.stage("生成建议", "正在核对候选并整理推荐依据"));
        AgentDecisionResult decision = timed(run.getRequestId(), "model",
                () -> callModelOrFallback(run.getRequest(), context, chatClientBuilder, run.getLoginUser()));
        AgentRequirement requirement = run.require(RUN_REQUIREMENT, AgentRequirement.class);
        requirement.setLastSelectedCardIds(decision.cards().stream().map(AgentCard::getCardId).toList());
        run.put(RUN_DECISION, decision);
        run.put(RUN_MODEL_ENABLED, chatClientBuilder != null);
        run.put(AgentGraphState.SELECTED_COUNT, decision.cards().size());
    }

    /** Final graph node: persist the answer in a second short transaction and build the API response. */
    @Override
    public void persistAnswer(AgentGraphRunContext run) {
        AgentTurnContext turn = run.require(RUN_TURN, AgentTurnContext.class);
        AgentRequirement requirement = run.require(RUN_REQUIREMENT, AgentRequirement.class);
        AgentContext context = run.require(RUN_CONTEXT, AgentContext.class);
        AgentDecisionResult decision = run.require(RUN_DECISION, AgentDecisionResult.class);
        Long userId = run.getLoginUser() == null ? 0L : run.getLoginUser().getId();
        timed(run.getRequestId(), "persist-answer", () -> {
            persistenceService.completeTurn(turn.conversationId(), userId,
                    decision.answer(), decision.cards(), requirement);
            return null;
        });
        requirementService.cache(turn.conversationId(), requirement);
        AgentContext selectedContext = context.withCards(decision.cards());
        AgentChatResponse response = new AgentChatResponse(
                turn.conversationId(),
                decision.answer(),
                decision.cards(),
                quickReplies(selectedContext),
                Boolean.TRUE.equals(run.getAttributes().get(RUN_MODEL_ENABLED)));
        run.put(AgentGraphState.RESPONSE, response);
        log.info("agent.total requestId={} conversationId={} elapsedMs={} cards={}",
                run.getRequestId(), turn.conversationId(), elapsedMillis(run.getStartedNanos()), decision.cards().size());
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
        requirementService.evict(conversationId);
    }

    /** Builds immutable query parameters once; graph branches only add their own card outputs. */
    private AgentContext baseContext(AgentChatRequest request,
                                     LoginUser loginUser,
                                     AgentRequirement requirement) {
        List<String> sportCodes = requirement.getSportCodes() == null ? List.of() : requirement.getSportCodes();
        String city = firstNotBlank(request.getCity(), loginUser == null ? null : loginUser.getCity(), "西安市");
        Double lng = request.getLng() != null ? request.getLng() : loginUser == null ? null : loginUser.getLongitude();
        Double lat = request.getLat() != null ? request.getLat() : loginUser == null ? null : loginUser.getLatitude();
        Integer budget = requirement.getMaxBudget() == null ? null : requirement.getMaxBudget().intValue();
        return new AgentContext(requirement, sportCodes, city, lng, lat, budget,
                timePreference(requirement), List.of());
    }

    /** A missing intent means broad discovery; otherwise only the matching graph branch does work. */
    private boolean shouldQuery(AgentContext context, String intent) {
        AgentRequirement requirement = context.requirement();
        Set<String> intents = new HashSet<>(requirement.getIntents() == null ? List.of() : requirement.getIntents());
        boolean broad = !intents.contains("PLACE")
                && !intents.contains("ACTIVITY")
                && !intents.contains("EQUIPMENT");
        return broad || intents.contains(intent);
    }

    private List<String> querySports(AgentContext context) {
        return context.sportCodes().isEmpty() ? List.of("") : context.sportCodes();
    }

    private List<AgentCard> venueProducts(List<AgentCard> places,
                                          String requestedSport,
                                          AgentRequirement requirement) {
        List<AgentCard> result = new ArrayList<>();
        for (AgentCard place : places.stream().limit(2).toList()) {
            Integer placeRank = placeRankOf(place);
            String sportCode = firstNotBlank(stringMeta(place, "sportCode"), requestedSport);
            if (placeRank == null || sportCode.isBlank()) {
                continue;
            }
            List<AgentCard> products = venueProductTool
                    .searchVenueProducts(sportCode, placeRank, null, requirement)
                    .stream()
                    .limit(1)
                    .toList();
            products.forEach(product -> attachPlaceContext(product, place));
            result.addAll(products);
        }
        return result;
    }

    private List<AgentCard> safeTool(String requestId,
                                     String step,
                                     Supplier<List<AgentCard>> supplier) {
        FutureTask<List<AgentCard>> task = new FutureTask<>(() -> timed(requestId, step, supplier));
        Thread worker = Thread.ofVirtual().name("agent-" + step).start(task);
        try {
            return task.get(Math.max(1, agentProperties.getToolTimeoutSeconds()), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            worker.interrupt();
            log.warn("agent.step requestId={} step={} fallback=empty reason=timeout", requestId, step);
            return List.of();
        } catch (InterruptedException ex) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("agent.step requestId={} step={} fallback=empty message={}",
                    requestId, step, cause.getMessage());
            return List.of();
        }
    }

    private <T> T timed(String requestId, String step, Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            T value = supplier.get();
            int count = value instanceof List<?> list ? list.size() : -1;
            log.info("agent.step requestId={} step={} elapsedMs={} count={}",
                    requestId, step, elapsedMillis(started), count);
            return value;
        } catch (RuntimeException ex) {
            log.warn("agent.step requestId={} step={} elapsedMs={} status=failed message={}",
                    requestId, step, elapsedMillis(started), ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String timePreference(AgentRequirement requirement) {
        if (requirement.getStartTime() == null) {
            return "";
        }
        int hour = requirement.getStartTime().getHour();
        if (hour < 12) return "MORNING";
        if (hour < 18) return "AFTERNOON";
        return "EVENING";
    }

    private void assignCardIds(List<AgentCard> cards) {
        for (AgentCard card : cards) {
            if (card.getCardId() != null && !card.getCardId().isBlank()) {
                continue;
            }
            String actionId = card.getAction() == null ? null : card.getAction().getId();
            String inventoryId = card.getMeta() == null ? null : String.valueOf(card.getMeta().getOrDefault("inventoryId", ""));
            String suffix = actionId == null || actionId.isBlank()
                    ? Integer.toUnsignedString(Objects.hash(card.getType(), card.getTitle()))
                    : actionId;
            card.setCardId(card.getType() + ":" + suffix + (inventoryId == null || inventoryId.isBlank() ? "" : ":" + inventoryId));
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data));
            }
        } catch (IOException ignored) {
            // The browser may leave the page while a model call is in flight; persistence still completes safely.
        }
    }

    private AgentDecisionResult callModelOrFallback(AgentChatRequest request,
                                                    AgentContext context,
                                                    ChatClient.Builder builder,
                                                    LoginUser loginUser) {
        if (context.cards().isEmpty()) {
            return new AgentDecisionResult(groundedAnswer(context), List.of());
        }
        List<AgentCard> fallback = deterministicSelection(context.cards());
        if (builder == null) {
            AgentContext selected = context.withCards(finalizeSelection(fallback, context));
            return new AgentDecisionResult(groundedAnswer(selected), selected.cards());
        }
        try {
            // Model receives opaque cardId values and human-readable facts, never database actions.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userMessage", request.getMessage());
            payload.put("requirement", context.requirement());
            payload.put("city", context.city());
            payload.put("cards", cardsForModel(context.cards()));
            payload.put("userPreference", userPreference(loginUser));
            String content = builder.build()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(writeJson(payload))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                AgentContext selected = context.withCards(finalizeSelection(fallback, context));
                return new AgentDecisionResult(groundedAnswer(selected), selected.cards());
            }
            AgentModelDecision modelDecision = objectMapper.readValue(stripJsonFence(content), AgentModelDecision.class);
            List<AgentCard> validated = validateSelection(modelDecision.getSelectedCardIds(), context.cards());
            if (validated.isEmpty()) {
                validated = fallback;
            }
            validated = finalizeSelection(validated, context);
            applyModelReasons(validated, modelDecision.getRecommendationReasons());
            // Overall explanation remains diagnostic; user-facing reasons are bound to validated card ids above.
            log.debug("Agent model selection explanation: {}", modelDecision.getExplanation());
            AgentContext selected = context.withCards(validated);
            return new AgentDecisionResult(groundedAnswer(selected), validated);
        } catch (Exception ex) {
            log.warn("Agent structured model output failed, using deterministic ranking: {}", ex.getMessage());
            AgentContext selected = context.withCards(finalizeSelection(fallback, context));
            return new AgentDecisionResult(groundedAnswer(selected), selected.cards());
        }
    }

    private ChatClient.Builder safeChatClientBuilder() {
        try {
            return chatClientBuilderProvider.getIfAvailable();
        } catch (BeansException ex) {
            return null;
        }
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
        List<AgentCard> placeCards = context.cards().stream()
                .filter(card -> AgentConstants.CARD_PLACE.equals(card.getType()))
                .limit(4)
                .toList();
        if (!placeCards.isEmpty()) {
            return groundedPlaceAnswer(placeCards);
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
                    .append("**").append(card.getTitle()).append("**")
                    .append(card.getPrice() == null ? "" : "（" + card.getPrice() + "）")
                    .append("：")
                    .append(firstNotBlank(card.getSubtitle(), "可点击商品卡片查看详情"));
            List<String> reasons = reasonTexts(card);
            if (!reasons.isEmpty()) {
                appendSentenceSeparator(answer);
                answer.append("**推荐理由：**").append(String.join("、", reasons));
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
                    .append("**").append(card.getTitle()).append("**")
                    .append(card.getPrice() == null ? "" : "（" + card.getPrice() + "）")
                    .append("：")
                    .append(venueName.isBlank() ? "" : venueName + "，")
                    .append(firstNotBlank(card.getSubtitle(), "可点击团购卡片查看详情"));
            Object timeMatch = card.getMeta().get("timeMatch");
            if (timeMatch != null) {
                appendSentenceSeparator(answer);
                answer.append("时段匹配：").append(timeMatch);
            }
            List<String> reasons = reasonTexts(card);
            if (!reasons.isEmpty()) {
                appendSentenceSeparator(answer);
                answer.append("**推荐理由：**").append(String.join("、", reasons));
            }
            appendSentenceSeparator(answer);
            answer.append("\n");
        }
        return answer.append("\n如果要下单，请点击对应团购卡片，前端会再让你确认。").toString();
    }

    private String groundedPlaceAnswer(List<AgentCard> cards) {
        StringBuilder answer = new StringBuilder("我按距离、营业信息和场馆条件为你筛选：\n\n");
        for (int i = 0; i < cards.size(); i++) {
            AgentCard card = cards.get(i);
            answer.append(i + 1)
                    .append(". ")
                    .append("**").append(card.getTitle()).append("**")
                    .append("：")
                    .append(firstNotBlank(card.getSubtitle(), "可点击场所卡片查看详情"));
            List<String> reasons = reasonTexts(card);
            if (!reasons.isEmpty()) {
                appendSentenceSeparator(answer);
                answer.append("**推荐理由：**").append(String.join("、", reasons));
            }
            appendSentenceSeparator(answer);
            answer.append("\n");
        }
        return answer.append("\n团购和时段以场所卡片中的平台实时可售数据为准。").toString();
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
        Object aiReason = card.getMeta().get("aiRecommendReason");
        if (aiReason instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
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

    /**
     * Binds model-generated reasons only to card ids that survived backend validation.
     * Duplicate or oversized reasons are ignored so one generic sentence cannot be copied
     * onto every recommendation and untrusted model output cannot expand the response.
     */
    private void applyModelReasons(List<AgentCard> cards, Map<String, String> modelReasons) {
        if (modelReasons == null || modelReasons.isEmpty()) {
            return;
        }
        Set<String> accepted = new HashSet<>();
        for (AgentCard card : cards) {
            String reason = sanitizeModelReason(modelReasons.get(card.getCardId()));
            String normalized = reason.toLowerCase(Locale.ROOT);
            if (reason.isBlank() || !accepted.add(normalized)) {
                continue;
            }
            card.getMeta().put("aiRecommendReason", reason);
        }
    }

    private String sanitizeModelReason(String reason) {
        if (reason == null) {
            return "";
        }
        String value = reason.replaceAll("[\\r\\n]+", " ")
                .replace("**", "")
                .trim();
        return value.length() <= 80 ? value : value.substring(0, 80);
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
                    data.put("cardId", card.getCardId());
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

    private List<AgentCard> validateSelection(List<String> selectedCardIds, List<AgentCard> candidates) {
        if (selectedCardIds == null || selectedCardIds.isEmpty()) {
            return List.of();
        }
        Map<String, AgentCard> byId = candidates.stream()
                .filter(card -> card.getCardId() != null && !card.getCardId().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        AgentCard::getCardId,
                        card -> card,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return selectedCardIds.stream()
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(4)
                .toList();
    }

    private List<AgentCard> deterministicSelection(List<AgentCard> cards) {
        return cards.stream()
                .sorted(Comparator.comparingInt(this::recommendScore).reversed())
                .limit(4)
                .toList();
    }

    private int recommendScore(AgentCard card) {
        Object value = card.getMeta() == null ? null : card.getMeta().get("recommendScore");
        return value instanceof Number number ? number.intValue() : 50;
    }

    /**
     * Applies backend business guarantees after the model chooses candidate ids.
     * The model may rank candidates, but it cannot shrink a place recommendation to one card
     * when multiple real places are available.
     */
    private List<AgentCard> finalizeSelection(List<AgentCard> selected, AgentContext context) {
        List<AgentCard> covered = ensurePlaceRecommendationCoverage(
                selected, context.cards(), context.requirement());
        return expandPlaceContext(covered, context.cards());
    }

    /**
     * Ensures place-oriented answers contain enough alternatives for comparison.
     * Places with a same-place, currently purchasable venue product are appended first.
     */
    private List<AgentCard> ensurePlaceRecommendationCoverage(List<AgentCard> selected,
                                                              List<AgentCard> candidates,
                                                              AgentRequirement requirement) {
        boolean placeIntent = requirement.getIntents() != null
                && requirement.getIntents().contains("PLACE");
        if (!placeIntent) {
            return selected;
        }
        List<AgentCard> placeCandidates = candidates.stream()
                .filter(card -> AgentConstants.CARD_PLACE.equals(card.getType()))
                .sorted(Comparator
                        .comparing((AgentCard place) -> hasVenueProduct(place, candidates)).reversed()
                        .thenComparing(Comparator.comparingInt(this::recommendScore).reversed()))
                .toList();
        int targetCount = Math.min(MIN_PLACE_RECOMMENDATIONS, placeCandidates.size());
        if (targetCount == 0) {
            return selected;
        }

        LinkedHashMap<String, AgentCard> completed = new LinkedHashMap<>();
        selected.forEach(card -> completed.putIfAbsent(card.getCardId(), card));
        Set<String> representedPlaces = new HashSet<>();
        selected.stream().map(this::placeIdOf).filter(id -> !id.isBlank()).forEach(representedPlaces::add);
        for (AgentCard place : placeCandidates) {
            if (representedPlaces.size() >= targetCount) {
                break;
            }
            String placeId = placeIdOf(place);
            if (!placeId.isBlank() && representedPlaces.add(placeId)) {
                completed.putIfAbsent(place.getCardId(), place);
            }
        }
        return completed.values().stream().limit(4).toList();
    }

    private boolean hasVenueProduct(AgentCard place, List<AgentCard> candidates) {
        String placeId = placeIdOf(place);
        return !placeId.isBlank() && candidates.stream()
                .filter(candidate -> AgentConstants.CARD_VENUE_PRODUCT.equals(candidate.getType()))
                .anyMatch(candidate -> Objects.equals(placeId, placeIdOf(candidate)));
    }

    private String placeIdOf(AgentCard card) {
        if (AgentConstants.CARD_PLACE.equals(card.getType()) && card.getAction() != null) {
            return firstNotBlank(card.getAction().getId(), "");
        }
        if (AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType()) && card.getMeta() != null) {
            return String.valueOf(card.getMeta().getOrDefault("placeId", ""));
        }
        return "";
    }

    /** Adds the real place card next to a selected venue product so the mobile card remains navigable. */
    private List<AgentCard> expandPlaceContext(List<AgentCard> selected, List<AgentCard> candidates) {
        LinkedHashMap<String, AgentCard> expanded = new LinkedHashMap<>();
        for (AgentCard card : selected) {
            if (AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType()) && card.getMeta() != null) {
                String placeId = String.valueOf(card.getMeta().getOrDefault("placeId", ""));
                candidates.stream()
                        .filter(candidate -> AgentConstants.CARD_PLACE.equals(candidate.getType()))
                        .filter(candidate -> candidate.getAction() != null
                                && Objects.equals(candidate.getAction().getId(), placeId))
                        .findFirst()
                        .ifPresent(place -> expanded.putIfAbsent(place.getCardId(), place));
            }
            expanded.putIfAbsent(card.getCardId(), card);
            if (AgentConstants.CARD_PLACE.equals(card.getType()) && card.getAction() != null) {
                String placeId = card.getAction().getId();
                // 模型只选场所时，也附带同一真实场所的最佳可售团购；前端无需再次猜测或查询。
                candidates.stream()
                        .filter(candidate -> AgentConstants.CARD_VENUE_PRODUCT.equals(candidate.getType()))
                        .filter(candidate -> candidate.getMeta() != null
                                && Objects.equals(String.valueOf(candidate.getMeta().getOrDefault("placeId", "")), placeId))
                        .max(Comparator.comparingInt(this::recommendScore))
                        .ifPresent(product -> expanded.putIfAbsent(product.getCardId(), product));
            }
        }
        return expanded.values().stream().limit(6).toList();
    }

    private String stripJsonFence(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private Map<String, Object> userPreference(LoginUser loginUser) {
        if (loginUser == null) {
            return Map.of();
        }
        Map<String, Object> preference = new LinkedHashMap<>();
        preference.put("city", loginUser.getCity());
        preference.put("level", loginUser.getLevel());
        return preference;
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

    private record AgentContext(AgentRequirement requirement,
                                List<String> sportCodes,
                                String city,
                                Double lng,
                                Double lat,
                                Integer budget,
                                String timePreference,
                                List<AgentCard> cards) {
        private AgentContext withCards(List<AgentCard> selectedCards) {
            return new AgentContext(requirement, sportCodes, city, lng, lat, budget, timePreference, selectedCards);
        }
    }

    private record AgentDecisionResult(String answer, List<AgentCard> cards) {
    }
}
