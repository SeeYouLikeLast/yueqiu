package com.hm.badminton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentChatRequest;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.dto.agent.AgentTurnContext;
import com.hm.badminton.mapper.agent.AgentConversationMapper;
import com.hm.badminton.mapper.agent.AgentMessageMapper;
import com.hm.badminton.service.agent.impl.AgentPersistenceService;
import com.hm.badminton.utils.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPersistenceServiceTest {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, Map<String, Double>> sortedSets = new HashMap<>();
    private AgentPersistenceService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L, 102L, 103L, 104L);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForList()).thenReturn(listOperations);
        when(redis.opsForZSet()).thenReturn(zSetOperations);

        when(valueOperations.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        when(listOperations.rightPush(anyString(), anyString())).thenAnswer(invocation -> {
            List<String> rows = lists.computeIfAbsent(invocation.getArgument(0), ignored -> new ArrayList<>());
            rows.add(invocation.getArgument(1));
            return (long) rows.size();
        });
        doAnswer(invocation -> null).when(listOperations).trim(anyString(), anyLong(), anyLong());
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation ->
                new ArrayList<>(lists.getOrDefault(invocation.getArgument(0), List.of())));

        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            sortedSets.computeIfAbsent(invocation.getArgument(0), ignored -> new LinkedHashMap<>())
                    .put(invocation.getArgument(1), invocation.getArgument(2));
            return true;
        });
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
            long start = invocation.getArgument(1);
            long end = invocation.getArgument(2);
            List<String> members = sortedSets.getOrDefault(invocation.getArgument(0), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (start >= members.size()) return Set.of();
            int last = end < 0 ? members.size() : Math.min(members.size(), (int) end + 1);
            return new LinkedHashSet<>(members.subList((int) start, last));
        });
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redis.delete(anyCollection())).thenAnswer(invocation -> {
            long removed = 0;
            for (String key : invocation.<java.util.Collection<String>>getArgument(0)) {
                if (values.remove(key) != null) removed++;
                if (lists.remove(key) != null) removed++;
            }
            return removed;
        });
        when(zSetOperations.remove(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            Map<String, Double> members = sortedSets.get(invocation.getArgument(0));
            if (members == null) return 0L;
            long removed = 0;
            Object[] arguments = invocation.getArguments();
            for (int index = 1; index < arguments.length; index++) {
                Object member = arguments[index];
                if (members.remove(String.valueOf(member)) != null) removed++;
            }
            return removed;
        });

        AgentProperties properties = new AgentProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AgentPersistenceService(conversationMapper, messageMapper, idGenerator,
                objectMapper, redis, properties);
    }

    @Test
    void guestShouldCreateAndRestoreRedisHistory() {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("今晚附近打羽毛球");
        String anonymousId = "guest-session-123456789012345";

        AgentTurnContext turn = service.beginTurn(0L, anonymousId, request);
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));
        AgentCard card = new AgentCard();
        card.setCardId("place:1");
        service.completeTurn(turn, "找到了一个场所", List.of(card), requirement);

        assertThat(turn.anonymous()).isTrue();
        assertThat(turn.conversationId()).isNegative();
        assertThat(service.conversations(0L, anonymousId)).singleElement()
                .satisfies(item -> assertThat(item.getTitle()).isEqualTo("今晚附近打羽毛球"));
        assertThat(service.messages(0L, anonymousId, turn.conversationId()))
                .extracting("role")
                .containsExactly("user", "assistant");
        assertThat(service.requirementsJson(turn)).contains("badminton");

        service.deleteConversation(0L, anonymousId, turn.conversationId());
        assertThat(service.conversations(0L, anonymousId)).isEmpty();
    }
}
