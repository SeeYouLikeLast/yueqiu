package com.hm.badminton.controller.agent;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentChatResponse;
import com.hm.badminton.dto.agent.AgentConversationView;
import com.hm.badminton.dto.agent.AgentMessageView;
import com.hm.badminton.service.agent.IAgentService;
import com.hm.badminton.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** AI 状态、对话、历史会话和消息查询的 HTTP 入口。 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final IAgentService agentService;
    private final UserContext userContext;

    public AgentController(IAgentService agentService, UserContext userContext) {
        this.agentService = agentService;
        this.userContext = userContext;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> status() {
        return ApiResponse.ok(Map.of("aiEnabled", agentService.aiEnabled()));
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request,
                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(agentService.chat(request, clientIp(servletRequest)));
    }

    /** Streams progress events immediately while tools and DashScope continue in the background. */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AgentChatRequest request,
                                 HttpServletRequest servletRequest) {
        return agentService.chatStream(request, clientIp(servletRequest));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversationView>> conversations() {
        return ApiResponse.ok(agentService.conversations(userContext.requireUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AgentMessageView>> messages(@PathVariable Long id) {
        return ApiResponse.ok(agentService.messages(userContext.requireUserId(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        agentService.deleteConversation(userContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
