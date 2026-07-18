package com.hm.badminton.service.agent;

import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentChatResponse;
import com.hm.badminton.dto.agent.AgentConversationView;
import com.hm.badminton.dto.agent.AgentMessageView;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IAgentService {
    boolean aiEnabled();

    AgentChatResponse chat(AgentChatRequest request, String clientIp);

    SseEmitter chatStream(AgentChatRequest request, String clientIp);

    List<AgentConversationView> conversations(Long userId);

    List<AgentMessageView> messages(Long userId, Long conversationId);

    void deleteConversation(Long userId, Long conversationId);
}
