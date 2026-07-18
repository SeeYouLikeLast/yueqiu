package com.hm.badminton.service.agent.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;

/**
 * StateGraph definition for one assistant turn.
 *
 * <p>The three independent business branches execute in parallel. The place branch also
 * queries venue products after it obtains real AMap places, preserving that dependency.</p>
 */
@Component
public class AgentGraphWorkflow {

    private static final String RUN_CONTEXT = "agentGraphRunContext";
    private static final String NODE_HANDLER = "agentGraphNodeHandler";

    private static final String BEGIN_TURN = "beginTurn";
    private static final String UNDERSTAND = "understandRequirement";
    private static final String DISPATCH = "dispatchTools";
    private static final String QUERY_PLACE = "queryPlacesAndProducts";
    private static final String QUERY_ACTIVITY = "queryActivities";
    private static final String QUERY_EQUIPMENT = "queryEquipment";
    private static final String MERGE = "mergeCandidates";
    private static final String SELECT = "selectCandidates";
    private static final String PERSIST = "persistAnswer";

    private final Executor graphExecutor;
    private final CompiledGraph graph;

    public AgentGraphWorkflow(@Qualifier("agentToolExecutor") Executor graphExecutor) {
        this.graphExecutor = graphExecutor;
        this.graph = compileGraph();
    }

    public AgentChatResponse execute(AgentGraphNodeHandler handler, AgentGraphRunContext run) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(run.getRequestId())
                .defaultParallelExecutor(graphExecutor)
                .build();
        config.context().put(RUN_CONTEXT, run);
        config.context().put(NODE_HANDLER, handler);
        try {
            graph.invoke(Map.of(AgentGraphState.REQUEST_ID, run.getRequestId()), config)
                    .orElseThrow(() -> new IllegalStateException("Agent graph returned no state"));
            return run.response();
        } catch (RuntimeException ex) {
            BusinessException businessException = findCause(ex, BusinessException.class);
            if (businessException != null) {
                throw businessException;
            }
            throw ex;
        }
    }

    private CompiledGraph compileGraph() {
        try {
            KeyStrategyFactory strategies = new KeyStrategyFactoryBuilder()
                    .defaultStrategy(new ReplaceStrategy())
                    .build();
            StateGraph stateGraph = new StateGraph("yueqiu-agent", strategies)
                    .addNode(BEGIN_TURN, node_async((state, config) -> {
                        handler(config).beginTurn(run(config));
                        return Map.of(AgentGraphState.CONVERSATION_ID,
                                run(config).getAttributes().get(AgentGraphState.CONVERSATION_ID));
                    }))
                    .addNode(UNDERSTAND, node_async((state, config) -> {
                        handler(config).understandRequirement(run(config));
                        return Map.of(AgentGraphState.REQUIREMENT_READY, true);
                    }))
                    .addNode(DISPATCH, node_async((state, config) -> {
                        handler(config).dispatchTools(run(config));
                        return Map.of("toolsDispatched", true);
                    }))
                    .addNode(QUERY_PLACE, node_async((state, config) -> {
                        AgentPlaceBranchResult result = handler(config).queryPlacesAndProducts(run(config));
                        return Map.of(
                                AgentGraphState.PLACE_CARDS, result.places(),
                                AgentGraphState.VENUE_PRODUCT_CARDS, result.venueProducts());
                    }))
                    .addNode(QUERY_ACTIVITY, node_async((state, config) -> Map.of(
                            AgentGraphState.ACTIVITY_CARDS, handler(config).queryActivities(run(config)))))
                    .addNode(QUERY_EQUIPMENT, node_async((state, config) -> Map.of(
                            AgentGraphState.EQUIPMENT_CARDS, handler(config).queryEquipment(run(config)))))
                    .addNode(MERGE, node_async((state, config) -> {
                        List<AgentCard> places = cards(state, AgentGraphState.PLACE_CARDS);
                        List<AgentCard> products = cards(state, AgentGraphState.VENUE_PRODUCT_CARDS);
                        List<AgentCard> activities = cards(state, AgentGraphState.ACTIVITY_CARDS);
                        List<AgentCard> equipment = cards(state, AgentGraphState.EQUIPMENT_CARDS);
                        handler(config).mergeCandidates(run(config), places, products, activities, equipment);
                        return Map.of(AgentGraphState.CANDIDATE_COUNT,
                                places.size() + products.size() + activities.size() + equipment.size());
                    }))
                    .addNode(SELECT, node_async((state, config) -> {
                        handler(config).selectCandidates(run(config));
                        return Map.of(AgentGraphState.SELECTED_COUNT,
                                run(config).getAttributes().getOrDefault(AgentGraphState.SELECTED_COUNT, 0));
                    }))
                    .addNode(PERSIST, node_async((state, config) -> {
                        handler(config).persistAnswer(run(config));
                        return Map.of("persisted", true);
                    }))
                    .addEdge(START, BEGIN_TURN)
                    .addEdge(BEGIN_TURN, UNDERSTAND)
                    .addEdge(UNDERSTAND, DISPATCH)
                    .addEdge(DISPATCH, QUERY_PLACE)
                    .addEdge(DISPATCH, QUERY_ACTIVITY)
                    .addEdge(DISPATCH, QUERY_EQUIPMENT)
                    .addEdge(QUERY_PLACE, MERGE)
                    .addEdge(QUERY_ACTIVITY, MERGE)
                    .addEdge(QUERY_EQUIPMENT, MERGE)
                    .addEdge(MERGE, SELECT)
                    .addEdge(SELECT, PERSIST)
                    .addEdge(PERSIST, END);
            // Conversation memory remains in MySQL/Redis. No in-memory graph checkpointer is
            // registered, so completed request states cannot accumulate in this singleton bean.
            return stateGraph.compile(CompileConfig.builder().releaseThread(true).build());
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Unable to compile AI assistant graph", ex);
        }
    }

    private AgentGraphRunContext run(RunnableConfig config) {
        return (AgentGraphRunContext) config.context().get(RUN_CONTEXT);
    }

    private AgentGraphNodeHandler handler(RunnableConfig config) {
        return (AgentGraphNodeHandler) config.context().get(NODE_HANDLER);
    }

    @SuppressWarnings("unchecked")
    private List<AgentCard> cards(OverAllState state, String key) {
        Object value = state.value(key).orElse(List.of());
        return value instanceof List<?> list ? (List<AgentCard>) list : List.of();
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
