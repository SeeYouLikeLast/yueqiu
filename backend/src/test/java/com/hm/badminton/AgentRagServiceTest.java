package com.hm.badminton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRagEvidence;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.entity.Blog;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.mapper.community.BlogMapper;
import com.hm.badminton.service.agent.impl.AgentRagService;
import com.hm.badminton.service.place.IVenueService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRagServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAttachOnlyEntityBoundBlogAndVenueReviewEvidence() {
        BlogMapper blogMapper = mock(BlogMapper.class);
        IVenueService venueService = mock(IVenueService.class);
        ObjectProvider<EmbeddingModel> embeddingProvider = mock(ObjectProvider.class);
        when(embeddingProvider.getIfAvailable()).thenReturn(null);

        Blog blog = new Blog()
                .setId(81L)
                .setSportCode("badminton")
                .setRelatedType("EQUIPMENT")
                .setRelatedId(11L)
                .setTitle("新手羽毛球鞋实测")
                .setContent("侧向支撑稳定，缓震适合新手训练")
                .setStatus(1);
        when(blogMapper.selectList(any())).thenReturn(List.of(blog));

        VenueReview review = new VenueReview()
                .setId(91L)
                .setRating(5)
                .setContent("灯光明亮，地胶防滑，适合新手");
        when(venueService.reviewsByPlaceSlot(anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(review));

        AgentProperties properties = new AgentProperties();
        properties.setRagEmbeddingEnabled(false);
        AgentRagService service = new AgentRagService(blogMapper, venueService, embeddingProvider,
                mock(StringRedisTemplate.class), new ObjectMapper(), properties);

        AgentCard equipment = card("equipment:11", AgentConstants.CARD_EQUIPMENT, "11");
        AgentCard place = card("place:amap-1", AgentConstants.CARD_PLACE, "amap-1");
        place.getMeta().put("placeRank", 1);
        place.getMeta().put("sportCode", "badminton");
        place.getMeta().put("city", "西安市");
        AgentRequirement requirement = new AgentRequirement();
        requirement.setSportCodes(List.of("badminton"));
        requirement.setPreferenceTags(List.of("新手友好", "灯光好"));

        List<AgentCard> enriched = service.enrich("推荐环境好、适合新手的场馆和装备",
                "西安市", requirement, List.of(equipment, place));

        List<AgentRagEvidence> equipmentEvidence = (List<AgentRagEvidence>) enriched.getFirst().getMeta().get("ragEvidence");
        List<AgentRagEvidence> placeEvidence = (List<AgentRagEvidence>) enriched.getLast().getMeta().get("ragEvidence");
        assertThat(equipmentEvidence).extracting(AgentRagEvidence::getSourceType).containsExactly("博客");
        assertThat(placeEvidence).extracting(AgentRagEvidence::getSourceType).containsExactly("场馆评价");
        assertThat(equipment.getMeta()).containsKeys("knowledgeHighlights", "knowledgeSources", "knowledgeScore");
        assertThat(place.getMeta()).containsKeys("knowledgeHighlights", "knowledgeSources", "knowledgeScore");
    }

    private AgentCard card(String cardId, String type, String actionId) {
        AgentCard card = new AgentCard();
        card.setCardId(cardId);
        card.setType(type);
        card.setTitle(cardId);
        card.setAction(AgentAction.of("OPEN", actionId));
        card.setMeta(new LinkedHashMap<>());
        return card;
    }
}
