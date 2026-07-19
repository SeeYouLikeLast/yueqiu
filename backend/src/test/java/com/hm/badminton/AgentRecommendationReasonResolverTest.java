package com.hm.badminton;

import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.service.agent.impl.AgentRecommendationReasonResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRecommendationReasonResolverTest {

    @Test
    void shouldKeepDifferentModelReasons() {
        AgentCard first = venueCard("venue:1", "导航羽毛球馆", "￥58", "靠近地铁，适合下班快打");
        AgentCard second = venueCard("venue:2", "高新羽毛球馆", "￥74", "灯光和地胶更好，适合进阶对抗");

        AgentRecommendationReasonResolver.BindingResult result = AgentRecommendationReasonResolver.bind(
                List.of(first, second),
                Map.of("venue:1", "距离地铁更近且价格更低", "venue:2", "地胶和灯光更适合进阶对抗"));

        assertThat(result.aiCount()).isEqualTo(2);
        assertThat(reason(first)).isNotEqualTo(reason(second));
        assertThat(source(first)).isEqualTo(AgentRecommendationReasonResolver.SOURCE_AI);
    }

    @Test
    void duplicatedModelReasonShouldUseCardSpecificFallback() {
        AgentCard first = venueCard("venue:1", "导航羽毛球馆", "￥58", "靠近地铁，适合下班快打");
        AgentCard second = venueCard("venue:2", "高新羽毛球馆", "￥74", "灯光和地胶更好，适合进阶对抗");
        String duplicated = "日期和时段完全匹配、支持按规则退款";

        AgentRecommendationReasonResolver.BindingResult result = AgentRecommendationReasonResolver.bind(
                List.of(first, second), Map.of("venue:1", duplicated, "venue:2", duplicated));

        assertThat(result.aiCount()).isEqualTo(1);
        assertThat(result.ruleCount()).isEqualTo(1);
        assertThat(reason(first)).isNotEqualTo(reason(second));
        assertThat(reason(second)).contains("高新羽毛球馆", "￥74", "灯光和地胶更好");
        assertThat(source(second)).isEqualTo(AgentRecommendationReasonResolver.SOURCE_RULE);
    }

    @Test
    void localFallbackShouldDifferentiateCandidatesByPriceAndFeatures() {
        AgentCard first = venueCard("venue:1", "导航羽毛球馆", "￥58", "靠近地铁，适合下班快打");
        AgentCard second = venueCard("venue:2", "高新羽毛球馆", "￥74", "灯光和地胶更好，适合进阶对抗");

        AgentRecommendationReasonResolver.BindingResult result =
                AgentRecommendationReasonResolver.bind(List.of(first, second), Map.of());

        assertThat(result.ruleCount()).isEqualTo(2);
        assertThat(reason(first)).contains("价格最低", "￥58");
        assertThat(reason(second)).contains("￥74", "灯光和地胶更好");
        assertThat(reason(first)).isNotEqualTo(reason(second));
    }

    private AgentCard venueCard(String id, String venueName, String price, String subtitle) {
        AgentCard card = new AgentCard();
        card.setCardId(id);
        card.setType("venue_product");
        card.setTitle("羽毛球 晚间黄金单场 1 小时");
        card.setSubtitle(subtitle);
        card.setPrice(price);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("venueName", venueName);
        meta.put("timeMatch", "完全匹配");
        card.setMeta(meta);
        return card;
    }

    private String reason(AgentCard card) {
        return String.valueOf(card.getMeta().get(AgentRecommendationReasonResolver.REASON_KEY));
    }

    private String source(AgentCard card) {
        return String.valueOf(card.getMeta().get(AgentRecommendationReasonResolver.REASON_SOURCE_KEY));
    }
}
