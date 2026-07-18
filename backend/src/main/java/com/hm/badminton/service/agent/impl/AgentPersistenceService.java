package com.hm.badminton.service.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.entity.AgentConversation;
import com.hm.badminton.entity.AgentMessage;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.mapper.agent.AgentMessageMapper;
import com.hm.badminton.service.agent.IAgentPersistenceService;
import com.hm.badminton.utils.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AgentPersistenceService implements IAgentPersistenceService {

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public AgentPersistenceService(AgentConversationMapper conversationMapper,
                                   AgentMessageMapper messageMapper,
                                   IdGenerator idGenerator,
                                   ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /** Creates/reuses the conversation and durably records the question before slow external calls. */
    @Override
    @Transactional
    public AgentTurnContext beginTurn(Long userId, AgentChatRequest request) {
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
        return new AgentTurnContext(conversation.getId(), userId);
    }

    /** Saves one completed answer and the merged requirement atomically. */
    @Override
    @Transactional
    public void completeTurn(Long conversationId,
                             Long userId,
                             String answer,
                             List<AgentCard> cards,
                             AgentRequirement requirement) {
        insertMessage(conversationId, userId, AgentConstants.ROLE_ASSISTANT, answer, cards);
        conversationMapper.updateById(new AgentConversation()
                .setId(conversationId)
                .setRequirementsJson(writeJson(requirement))
                .setUpdatedAt(LocalDateTime.now()));
    }

    private AgentConversation findOwnedConversation(Long userId, Long conversationId) {
        if (conversationId == null) {
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
        AgentMessage message = new AgentMessage()
                .setId(idGenerator.nextId())
                .setConversationId(conversationId)
                .setUserId(userId)
                .setRole(role)
                .setContent(content)
                .setCardsJson(cards == null || cards.isEmpty() ? null : writeJson(cards))
                .setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
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
}
