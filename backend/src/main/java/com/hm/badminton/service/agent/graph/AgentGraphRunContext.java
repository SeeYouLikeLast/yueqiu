package com.hm.badminton.service.agent.graph;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentChatResponse;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Request-scoped values that must not be checkpointed by StateGraph.
 *
 * <p>The graph state contains business node outputs. Runtime-only collaborators such as
 * the SSE consumer and the current login user stay here and are passed through
 * {@code RunnableConfig.context()}.</p>
 */
@Getter
public final class AgentGraphRunContext {

    private final String requestId = UUID.randomUUID().toString().substring(0, 8);
    private final long startedNanos = System.nanoTime();
    private final AgentChatRequest request;
    private final String clientIp;
    private final String anonymousId;
    private final LoginUser loginUser;
    private final Consumer<AgentGraphProgress> progressConsumer;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public AgentGraphRunContext(AgentChatRequest request,
                                String clientIp,
                                String anonymousId,
                                LoginUser loginUser,
                                Consumer<AgentGraphProgress> progressConsumer) {
        this.request = request;
        this.clientIp = clientIp;
        this.anonymousId = anonymousId;
        this.loginUser = loginUser;
        this.progressConsumer = progressConsumer;
    }

    public void emit(AgentGraphProgress progress) {
        progressConsumer.accept(progress);
    }

    public void put(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public <T> T require(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Missing agent graph value: " + key);
        }
        return type.cast(value);
    }

    public AgentChatResponse response() {
        return require(AgentGraphState.RESPONSE, AgentChatResponse.class);
    }
}
