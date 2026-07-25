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
    private static final String RAG = "enrichKnowledge";
    private static final String SCORE = "scoreCandidates";
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
            // Graph 节点之间通过同名状态键传值；ReplaceStrategy 表示新值覆盖旧值。
            KeyStrategyFactory strategies = new KeyStrategyFactoryBuilder()
                    .defaultStrategy(new ReplaceStrategy())
                    .build();
            StateGraph stateGraph = new StateGraph("yueqiu-agent", strategies)
                    // 1. 创建/恢复会话，并先持久化用户本轮问题。
                    .addNode(BEGIN_TURN, node_async((state, config) -> {
                        handler(config).beginTurn(run(config));
                        return Map.of(AgentGraphState.CONVERSATION_ID,
                                run(config).getAttributes().get(AgentGraphState.CONVERSATION_ID));
                    }))
                    // 2. 把自然语言转换为可查询的结构化需求。
                    .addNode(UNDERSTAND, node_async((state, config) -> {
                        handler(config).understandRequirement(run(config));
                        return Map.of(AgentGraphState.REQUIREMENT_READY, true);
                    }))
                    // 3. 根据意图通知前端接下来要查询哪些业务分支。
                    .addNode(DISPATCH, node_async((state, config) -> {
                        handler(config).dispatchTools(run(config));
                        return Map.of("toolsDispatched", true);
                    }))
                    // 4. 场所、活动、装备三个节点互不依赖，由 StateGraph 并行执行。
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
                    // 5. 汇总三个分支产生的真实业务卡片，后面的模型只能从这些候选中选择。
                    .addNode(MERGE, node_async((state, config) -> {
                        List<AgentCard> places = cards(state, AgentGraphState.PLACE_CARDS);
                        List<AgentCard> products = cards(state, AgentGraphState.VENUE_PRODUCT_CARDS);
                        List<AgentCard> activities = cards(state, AgentGraphState.ACTIVITY_CARDS);
                        List<AgentCard> equipment = cards(state, AgentGraphState.EQUIPMENT_CARDS);
                        handler(config).mergeCandidates(run(config), places, products, activities, equipment);
                        return Map.of(AgentGraphState.CANDIDATE_COUNT,
                                places.size() + products.size() + activities.size() + equipment.size());
                    }))
                    // 6. RAG 只补充博客、评价、装备心得等软知识，不产生价格和库存。
                    .addNode(RAG, node_async((state, config) -> {
                        handler(config).enrichKnowledge(run(config));
                        return Map.of(AgentGraphState.RAG_EVIDENCE_COUNT,
                                run(config).getAttributes().getOrDefault(AgentGraphState.RAG_EVIDENCE_COUNT, 0));
                    }))
                    // 7. 后端评分、模型选择并生成解释，最后保存回答和结构化记忆。
                    .addNode(SCORE, node_async((state, config) -> {
                        handler(config).scoreCandidates(run(config));
                        return Map.of(AgentGraphState.SCORED_COUNT,
                                run(config).getAttributes().getOrDefault(AgentGraphState.SCORED_COUNT, 0));
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
                    .addEdge(MERGE, RAG)
                    .addEdge(RAG, SCORE)
                    .addEdge(SCORE, SELECT)
                    .addEdge(SELECT, PERSIST)
                    .addEdge(PERSIST, END);
            // 会话记忆保存在 MySQL/Redis，不注册 Graph 内存检查点，避免单例服务长期积累请求状态。
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
