package com.hm.badminton.service.agent.tools;

import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.vo.AgentVenueProductVO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds venue cards only from concrete, currently purchasable inventory rows. */
@Component
public class VenueProductAgentTool {

    private final IVenueItemService venueItemService;

    public VenueProductAgentTool(IVenueItemService venueItemService) {
        this.venueItemService = venueItemService;
    }

    @Tool(name = "searchVenueProducts", description = "Search real purchasable venue inventory slots.")
    public List<AgentCard> searchVenueProducts(String sportCode,
                                               Integer placeRank,
                                               String productType,
                                               Integer maxPrice) {
        AgentRequirement requirement = new AgentRequirement();
        requirement.setMaxBudget(maxPrice == null ? null : BigDecimal.valueOf(maxPrice));
        return searchVenueProducts(sportCode, placeRank, productType, requirement);
    }

    public List<AgentCard> searchVenueProducts(String sportCode,
                                               Integer placeRank,
                                               String productType,
                                               AgentRequirement requirement) {
        if (sportCode == null || sportCode.isBlank() || placeRank == null) {
            return List.of();
        }
        List<AgentVenueProductVO> candidates = venueItemService.agentCandidates(
                sportCode,
                placeRank,
                requirement.getTargetDate(),
                requirement.getStartTime(),
                requirement.getEndTime(),
                requirement.getMaxBudget(),
                6);
        return candidates.stream()
                .filter(item -> productType == null || productType.isBlank() || productType.equals(item.getProductType()))
                .map(this::toCard)
                .toList();
    }

    private AgentCard toCard(AgentVenueProductVO item) {
        AgentCard card = new AgentCard();
        card.setCardId("venue:" + item.getProductId() + ":" + item.getInventoryId());
        card.setType(AgentConstants.CARD_VENUE_PRODUCT);
        card.setTitle(item.getTitle());
        card.setSubtitle(item.getDescription());
        card.setCoverUrl(item.getCoverUrl());
        card.setPrice(yuan(item.getPrice()));
        card.setTags(tags(item));

        AgentAction action = AgentAction.of(AgentConstants.ACTION_OPEN_VENUE_PRODUCT, item.getProductId());
        action.getPayload().put("sportCode", item.getSportCode());
        action.getPayload().put("placeRank", item.getPlaceRank());
        action.getPayload().put("productType", item.getProductType());
        action.getPayload().put("inventoryId", item.getInventoryId());
        card.setAction(action);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", item.getProductId());
        meta.put("inventoryId", item.getInventoryId());
        meta.put("sportCode", item.getSportCode());
        meta.put("placeRank", item.getPlaceRank());
        meta.put("productType", item.getProductType());
        meta.put("venueName", item.getVenueName());
        meta.put("available", true);
        meta.put("serviceDate", item.getServiceDate());
        meta.put("startTime", item.getStartTime());
        meta.put("endTime", item.getEndTime());
        meta.put("timeMatch", timeMatch(item));
        meta.put("suitableLevel", suitableLevel(item.getProductType()));
        meta.put("sceneTags", tags(item));
        meta.put("pros", reasons(item));
        meta.put("cons", cons(item));
        meta.put("recommendReasons", reasons(item));
        card.setMeta(meta);
        return card;
    }

    private List<String> tags(AgentVenueProductVO item) {
        List<String> values = new ArrayList<>();
        values.add(productTypeName(item.getProductType()));
        if (item.getTags() != null && !item.getTags().isBlank()) {
            values.addAll(Arrays.stream(item.getTags().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList());
        }
        values.add(item.getServiceDate() + " " + item.getStartTime() + "-" + item.getEndTime());
        return values.stream().distinct().limit(5).toList();
    }

    private List<String> reasons(AgentVenueProductVO item) {
        List<String> reasons = new ArrayList<>();
        if ("EXACT".equals(item.getMatchType())) {
            reasons.add("日期和时段完全匹配");
        } else if ("ONE_HOUR_FALLBACK".equals(item.getMatchType())) {
            reasons.add("原时段无完整库存，回退为同日单场一小时");
        } else {
            reasons.add("存在可购买的真实库存时段");
        }
        if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.valueOf(60)) <= 0) {
            reasons.add("同类项目中价格更适合预算优先");
        } else if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.valueOf(70)) >= 0) {
            reasons.add("价格较高，更适合优先考虑场地体验");
        }
        if (item.getRefundRule() != null && item.getRefundRule().contains("退")) {
            reasons.add("支持按规则退款");
        }
        return reasons.stream().limit(3).toList();
    }

    private List<String> cons(AgentVenueProductVO item) {
        if ("ONE_HOUR_FALLBACK".equals(item.getMatchType())) {
            return List.of("不是原始完整时段，下单前请核对卡片时间");
        }
        return List.of();
    }

    private String timeMatch(AgentVenueProductVO item) {
        return "EXACT".equals(item.getMatchType()) ? "完全匹配" :
                "ONE_HOUR_FALLBACK".equals(item.getMatchType()) ? "同日单场一小时" : "最近可售时段";
    }

    private String suitableLevel(String productType) {
        return switch (productType == null ? "" : productType) {
            case "COACH_LESSON" -> "新手/进阶";
            case "COURT_SLOT" -> "不限";
            default -> "新手/日常练习";
        };
    }

    private String productTypeName(String type) {
        return switch (type == null ? "" : type) {
            case "TIME_PACKAGE" -> "畅打套餐";
            case "COURT_SLOT" -> "单场时段";
            case "COACH_LESSON" -> "私教课";
            default -> "场馆项目";
        };
    }

    private String yuan(BigDecimal value) {
        return value == null ? null : "¥" + value.stripTrailingZeros().toPlainString();
    }
}
