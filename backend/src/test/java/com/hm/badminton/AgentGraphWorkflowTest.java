package com.hm.badminton;

import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentChatResponse;
import com.hm.badminton.service.agent.graph.AgentGraphNodeHandler;
import com.hm.badminton.service.agent.graph.AgentGraphRunContext;
import com.hm.badminton.service.agent.graph.AgentGraphState;
import com.hm.badminton.service.agent.graph.AgentGraphWorkflow;
import com.hm.badminton.service.agent.graph.AgentPlaceBranchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGraphWorkflowTest {

    @Test
    void shouldRunToolBranchesInParallelAndMergeTheirCards() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            AgentGraphWorkflow workflow = new AgentGraphWorkflow(executor);
            CountDownLatch parallelBranches = new CountDownLatch(3);
            RecordingHandler handler = new RecordingHandler(parallelBranches);
            AgentChatRequest request = new AgentChatRequest();
            request.setMessage("今晚附近打羽毛球");
            AgentGraphRunContext run = new AgentGraphRunContext(request, "127.0.0.1", "guest-test-id-1234567890", null, ignored -> {
            });

            AgentChatResponse response = workflow.execute(handler, run);

            assertThat(response.getConversationId()).isEqualTo(7L);
            assertThat(response.getCards()).extracting(AgentCard::getCardId)
                    .containsExactly("place:1", "venue:1", "activity:1", "equipment:1");
            assertThat(handler.mergedCount).isEqualTo(4);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class RecordingHandler implements AgentGraphNodeHandler {

        private final CountDownLatch branches;
        private int mergedCount;

        private RecordingHandler(CountDownLatch branches) {
            this.branches = branches;
        }

        @Override
        public void beginTurn(AgentGraphRunContext run) {
            run.put(AgentGraphState.CONVERSATION_ID, 7L);
        }

        @Override
        public void understandRequirement(AgentGraphRunContext run) {
        }

        @Override
        public void dispatchTools(AgentGraphRunContext run) {
        }

        @Override
        public AgentPlaceBranchResult queryPlacesAndProducts(AgentGraphRunContext run) {
            awaitOtherBranches();
            return new AgentPlaceBranchResult(List.of(card("place:1")), List.of(card("venue:1")));
        }

        @Override
        public List<AgentCard> queryActivities(AgentGraphRunContext run) {
            awaitOtherBranches();
            return List.of(card("activity:1"));
        }

        @Override
        public List<AgentCard> queryEquipment(AgentGraphRunContext run) {
            awaitOtherBranches();
            return List.of(card("equipment:1"));
        }

        @Override
        public void mergeCandidates(AgentGraphRunContext run,
                                    List<AgentCard> places,
                                    List<AgentCard> venueProducts,
                                    List<AgentCard> activities,
                                    List<AgentCard> equipment) {
            List<AgentCard> cards = java.util.stream.Stream.of(places, venueProducts, activities, equipment)
                    .flatMap(List::stream)
                    .toList();
            mergedCount = cards.size();
            run.put("mergedCards", cards);
        }

        @Override
        public void enrichKnowledge(AgentGraphRunContext run) {
            run.put(AgentGraphState.RAG_EVIDENCE_COUNT, 0);
        }

        @Override
        public void scoreCandidates(AgentGraphRunContext run) {
            @SuppressWarnings("unchecked")
            List<AgentCard> cards = (List<AgentCard>) run.getAttributes().get("mergedCards");
            run.put(AgentGraphState.SCORED_COUNT, cards.size());
        }

        @Override
        public void selectCandidates(AgentGraphRunContext run) {
            @SuppressWarnings("unchecked")
            List<AgentCard> cards = (List<AgentCard>) run.getAttributes().get("mergedCards");
            run.put(AgentGraphState.SELECTED_COUNT, cards.size());
        }

        @Override
        public void persistAnswer(AgentGraphRunContext run) {
            @SuppressWarnings("unchecked")
            List<AgentCard> cards = (List<AgentCard>) run.getAttributes().get("mergedCards");
            run.put(AgentGraphState.RESPONSE,
                    new AgentChatResponse(7L, "ok", cards, List.of(), false));
        }

        private void awaitOtherBranches() {
            branches.countDown();
            try {
                if (!branches.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Graph tool nodes did not run in parallel");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }

        private AgentCard card(String cardId) {
            AgentCard card = new AgentCard();
            card.setCardId(cardId);
            return card;
        }
    }
}
