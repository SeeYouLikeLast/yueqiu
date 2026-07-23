package com.hm.badminton.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentConversationView;
import com.hm.badminton.dto.agent.AgentMessageView;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.entity.AgentConversation;
import com.hm.badminton.entity.AgentMessage;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.mapper.agent.AgentMessageMapper;
import com.hm.badminton.service.agent.IAgentPersistenceService;
import com.hm.badminton.utils.IdGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persists authenticated conversations in MySQL and anonymous conversations in Redis.
 *
 * <p>Anonymous conversation ids are negative, so they can share the HTTP contract with
 * MySQL ids without ever colliding. Every anonymous key also contains the browser's
 * unguessable anonymous id; knowing a conversation id alone is not enough to read it.</p>
 */
@Service
public class AgentPersistenceService implements IAgentPersistenceService {

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Duration anonymousHistoryTtl;
    private final int historyLimit;
    private final int messageLimit;

    public AgentPersistenceService(AgentConversationMapper conversationMapper,
                                   AgentMessageMapper messageMapper,
                                   IdGenerator idGenerator,
                                   ObjectMapper objectMapper,
                                   StringRedisTemplate redisTemplate,
                                   AgentProperties properties) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.anonymousHistoryTtl = Duration.ofDays(Math.max(1, properties.getAnonymousHistoryTtlDays()));
        this.historyLimit = Math.max(1, properties.getAnonymousMaxConversations());
        this.messageLimit = Math.max(10, properties.getAnonymousMaxMessagesPerConversation());
    }

    /** Records the question before external AMap, RAG and model calls begin. */
    @Override
    @Transactional
    public AgentTurnContext beginTurn(Long userId, String anonymousId, AgentChatRequest request) {
        return userId != null && userId > 0
                ? beginAuthenticatedTurn(userId, request)
                : beginAnonymousTurn(requireAnonymousId(anonymousId), request);
    }

    protected AgentTurnContext beginAuthenticatedTurn(Long userId, AgentChatRequest request) {
        AgentConversation conversation = findOwnedConversation(userId, request.getConversationId());
        LocalDateTime now = LocalDateTime.now();
        if (conversation == null) {
            conversation = new AgentConversation()
                    .setId(idGenerator.nextId())
                    .setUserId(userId)
                    .setTitle(titleOf(request.getMessage()))
                    .setStatus(1)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            conversationMapper.insert(conversation);
        }
        insertMessage(conversation.getId(), userId, AgentConstants.ROLE_USER, request.getMessage(), null);
        return new AgentTurnContext(conversation.getId(), userId, null);
    }

    private AgentTurnContext beginAnonymousTurn(String anonymousId, AgentChatRequest request) {
        AnonymousConversation conversation = readAnonymousConversation(anonymousId, request.getConversationId());
        LocalDateTime now = LocalDateTime.now();
        if (conversation == null) {
            long id = negativeId(idGenerator.nextId());
            conversation = new AnonymousConversation(id, titleOf(request.getMessage()), null, now, now);
        }
        appendAnonymousMessage(anonymousId, conversation.id(), AgentConstants.ROLE_USER,
                request.getMessage(), null, now);
        saveAnonymousConversation(anonymousId, conversation.withUpdatedAt(now));
        return new AgentTurnContext(conversation.id(), 0L, anonymousId);
    }

    /** Stores the assistant answer and merged requirement after all slow calls have finished. */
    @Override
    @Transactional
    public void completeTurn(AgentTurnContext turn,
                             String answer,
                             List<AgentCard> cards,
                             AgentRequirement requirement) {
        if (turn.anonymous()) {
            completeAnonymousTurn(turn, answer, cards, requirement);
        } else {
            completeAuthenticatedTurn(turn, answer, cards, requirement);
        }
    }

    protected void completeAuthenticatedTurn(AgentTurnContext turn,
                                             String answer,
                                             List<AgentCard> cards,
                                             AgentRequirement requirement) {
        insertMessage(turn.conversationId(), turn.userId(), AgentConstants.ROLE_ASSISTANT, answer, cards);
        conversationMapper.updateById(new AgentConversation()
                .setId(turn.conversationId())
                .setRequirementsJson(writeJson(requirement))
                .setUpdatedAt(LocalDateTime.now()));
    }

    private void completeAnonymousTurn(AgentTurnContext turn,
                                       String answer,
                                       List<AgentCard> cards,
                                       AgentRequirement requirement) {
        String anonymousId = requireAnonymousId(turn.anonymousId());
        AnonymousConversation conversation = readAnonymousConversation(anonymousId, turn.conversationId());
        if (conversation == null) {
            throw new BusinessException(404, "匿名会话已过期，请开始新对话");
        }
        LocalDateTime now = LocalDateTime.now();
        appendAnonymousMessage(anonymousId, turn.conversationId(), AgentConstants.ROLE_ASSISTANT,
                answer, cards, now);
        saveAnonymousConversation(anonymousId,
                conversation.withRequirement(writeJson(requirement)).withUpdatedAt(now));
    }

    @Override
    public List<AgentConversationView> conversations(Long userId, String anonymousId) {
        if (userId != null && userId > 0) {
            return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                            .eq(AgentConversation::getUserId, userId)
                            .eq(AgentConversation::getStatus, 1)
                            .orderByDesc(AgentConversation::getUpdatedAt)
                            .last("limit " + historyLimit))
                    .stream().map(this::toConversationView).toList();
        }
        String owner = requireAnonymousId(anonymousId);
        Set<String> ids = redisTemplate.opsForZSet().reverseRange(indexKey(owner), 0, historyLimit - 1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<AgentConversationView> result = new ArrayList<>();
        for (String value : ids) {
            try {
                AnonymousConversation conversation = readAnonymousConversation(owner, Long.valueOf(value));
                if (conversation == null) {
                    redisTemplate.opsForZSet().remove(indexKey(owner), value);
                } else {
                    result.add(conversation.toView());
                }
            } catch (NumberFormatException ignored) {
                redisTemplate.opsForZSet().remove(indexKey(owner), value);
            }
        }
        touchIndex(owner);
        return result;
    }

    @Override
    public List<AgentMessageView> messages(Long userId, String anonymousId, Long conversationId) {
        if (userId != null && userId > 0) {
            AgentConversation conversation = findOwnedConversation(userId, conversationId);
            if (conversation == null) {
                throw new BusinessException(404, "会话不存在");
            }
            return messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                            .eq(AgentMessage::getConversationId, conversationId)
                            .orderByAsc(AgentMessage::getCreatedAt))
                    .stream().map(this::toMessageView).toList();
        }
        String owner = requireAnonymousId(anonymousId);
        if (readAnonymousConversation(owner, conversationId) == null) {
            throw new BusinessException(404, "匿名会话不存在或已过期");
        }
        List<String> rows = redisTemplate.opsForList().range(messagesKey(owner, conversationId), 0, -1);
        touchAnonymousConversation(owner, conversationId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::readMessageView).filter(Objects::nonNull).toList();
    }

    @Override
    public void deleteConversation(Long userId, String anonymousId, Long conversationId) {
        if (userId != null && userId > 0) {
            AgentConversation conversation = findOwnedConversation(userId, conversationId);
            if (conversation == null) {
                throw new BusinessException(404, "会话不存在");
            }
            conversationMapper.updateById(new AgentConversation()
                    .setId(conversationId)
                    .setStatus(0)
                    .setUpdatedAt(LocalDateTime.now()));
            return;
        }
        String owner = requireAnonymousId(anonymousId);
        if (readAnonymousConversation(owner, conversationId) == null) {
            throw new BusinessException(404, "匿名会话不存在或已过期");
        }
        redisTemplate.delete(List.of(conversationKey(owner, conversationId), messagesKey(owner, conversationId)));
        redisTemplate.opsForZSet().remove(indexKey(owner), String.valueOf(conversationId));
    }

    @Override
    public String requirementsJson(AgentTurnContext turn) {
        if (turn == null) {
            return null;
        }
        if (!turn.anonymous()) {
            AgentConversation conversation = findOwnedConversation(turn.userId(), turn.conversationId());
            return conversation == null ? null : conversation.getRequirementsJson();
        }
        AnonymousConversation conversation = readAnonymousConversation(
                requireAnonymousId(turn.anonymousId()), turn.conversationId());
        return conversation == null ? null : conversation.requirementsJson();
    }

    private AgentConversation findOwnedConversation(Long userId, Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            return null;
        }
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !Objects.equals(conversation.getUserId(), userId)
                || !Integer.valueOf(1).equals(conversation.getStatus())) {
            return null;
        }
        return conversation;
    }

    private void insertMessage(Long conversationId,
                               Long userId,
                               String role,
                               String content,
                               List<AgentCard> cards) {
        messageMapper.insert(new AgentMessage()
                .setId(idGenerator.nextId())
                .setConversationId(conversationId)
                .setUserId(userId)
                .setRole(role)
                .setContent(content)
                .setCardsJson(cards == null || cards.isEmpty() ? null : writeJson(cards))
                .setCreatedAt(LocalDateTime.now()));
    }

    private void appendAnonymousMessage(String anonymousId,
                                        Long conversationId,
                                        String role,
                                        String content,
                                        List<AgentCard> cards,
                                        LocalDateTime now) {
        AgentMessageView view = new AgentMessageView();
        view.setId(negativeId(idGenerator.nextId()));
        view.setRole(role);
        view.setContent(content);
        view.setCards(cards == null ? List.of() : cards);
        view.setCreatedAt(now);
        String key = messagesKey(anonymousId, conversationId);
        redisTemplate.opsForList().rightPush(key, writeJson(view));
        redisTemplate.opsForList().trim(key, -messageLimit, -1);
        redisTemplate.expire(key, anonymousHistoryTtl);
    }

    private void saveAnonymousConversation(String anonymousId, AnonymousConversation conversation) {
        redisTemplate.opsForValue().set(conversationKey(anonymousId, conversation.id()),
                writeJson(conversation), anonymousHistoryTtl);
        redisTemplate.opsForZSet().add(indexKey(anonymousId), String.valueOf(conversation.id()),
                toScore(conversation.updatedAt()));
        pruneAnonymousHistory(anonymousId);
        touchIndex(anonymousId);
    }

    private void pruneAnonymousHistory(String anonymousId) {
        Set<String> expired = redisTemplate.opsForZSet().reverseRange(indexKey(anonymousId), historyLimit, -1);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (String id : expired) {
            try {
                Long conversationId = Long.valueOf(id);
                redisTemplate.delete(List.of(
                        conversationKey(anonymousId, conversationId),
                        messagesKey(anonymousId, conversationId)));
            } catch (NumberFormatException ignored) {
                // Invalid members are removed from the index below as well.
            }
        }
        redisTemplate.opsForZSet().remove(indexKey(anonymousId), expired.toArray());
    }

    private AnonymousConversation readAnonymousConversation(String anonymousId, Long conversationId) {
        if (conversationId == null || conversationId >= 0) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(conversationKey(anonymousId, conversationId));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AnonymousConversation.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void touchAnonymousConversation(String anonymousId, Long conversationId) {
        redisTemplate.expire(conversationKey(anonymousId, conversationId), anonymousHistoryTtl);
        redisTemplate.expire(messagesKey(anonymousId, conversationId), anonymousHistoryTtl);
        touchIndex(anonymousId);
    }

    private void touchIndex(String anonymousId) {
        redisTemplate.expire(indexKey(anonymousId), anonymousHistoryTtl);
    }

    private String indexKey(String anonymousId) {
        return RedisConstants.AGENT_ANONYMOUS_CONVERSATIONS_KEY + anonymousId;
    }

    private String conversationKey(String anonymousId, Long conversationId) {
        return RedisConstants.AGENT_ANONYMOUS_CONVERSATION_KEY + anonymousId + ":" + conversationId;
    }

    private String messagesKey(String anonymousId, Long conversationId) {
        return RedisConstants.AGENT_ANONYMOUS_MESSAGES_KEY + anonymousId + ":" + conversationId;
    }

    private String requireAnonymousId(String anonymousId) {
        if (anonymousId == null || !anonymousId.matches("[A-Za-z0-9_-]{20,64}")) {
            throw new BusinessException(400, "匿名会话标识无效，请刷新页面后重试");
        }
        return anonymousId;
    }

    private long negativeId(long value) {
        return value == Long.MIN_VALUE ? Long.MIN_VALUE + 1 : -Math.abs(value);
    }

    private double toScore(LocalDateTime value) {
        return value.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
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

    private AgentMessageView readMessageView(String json) {
        try {
            return objectMapper.readValue(json, AgentMessageView.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private List<AgentCard> readCards(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
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

    private record AnonymousConversation(Long id,
                                         String title,
                                         String requirementsJson,
                                         LocalDateTime createdAt,
                                         LocalDateTime updatedAt) {

        private AnonymousConversation withUpdatedAt(LocalDateTime value) {
            return new AnonymousConversation(id, title, requirementsJson, createdAt, value);
        }

        private AnonymousConversation withRequirement(String value) {
            return new AnonymousConversation(id, title, value, createdAt, updatedAt);
        }

        private AgentConversationView toView() {
            AgentConversationView view = new AgentConversationView();
            view.setId(id);
            view.setTitle(title);
            view.setCreatedAt(createdAt);
            view.setUpdatedAt(updatedAt);
            return view;
        }
    }
}
