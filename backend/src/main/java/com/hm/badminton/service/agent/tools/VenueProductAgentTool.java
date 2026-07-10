package com.hm.badminton.service.agent.tools;

import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.VenueItem;
import com.hm.badminton.service.trade.IVenueItemService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VenueProductAgentTool {

    private final IVenueItemService venueItemService;

    public VenueProductAgentTool(IVenueItemService venueItemService) {
        this.venueItemService = venueItemService;
    }

    @Tool(name = "searchVenueProducts", description = "Search purchasable venue products, court slots and coach lessons.")
    public List<AgentCard> searchVenueProducts(String sportCode,
                                               Integer placeRank,
                                               String productType,
                                               Integer maxPrice) {
        List<VenueItem> items = placeRank == null
                ? venueItemService.saleItems(blankToNull(sportCode), blankToNull(productType), null, 1, 6).getRecords()
                : venueItemService.items(blankToNull(sportCode), null, null, placeRank, 6);
        List<AgentCard> cards = new ArrayList<>();
        for (VenueItem item : items) {
            if (maxPrice != null && item.getPrice() != null
                    && item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) > 0) {
                continue;
            }
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_VENUE_PRODUCT);
            card.setTitle(item.getTitle());
            card.setSubtitle(item.getDescription());
            card.setCoverUrl(item.getCoverUrl());
            card.setPrice(yuan(item.getPrice()));
            List<String> tags = item.getTags() == null || item.getTags().isEmpty()
                    ? List.of(nullToText(item.getProductTypeName()))
                    : item.getTags();
            card.setTags(tags);
            AgentAction action = AgentAction.of(AgentConstants.ACTION_OPEN_VENUE_PRODUCT, item.getId());
            action.getPayload().put("sportCode", item.getSportCode());
            action.getPayload().put("placeRank", item.getPlaceRank());
            action.getPayload().put("productType", item.getProductType());
            card.setAction(action);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", item.getId());
            meta.put("sportCode", item.getSportCode());
            meta.put("placeRank", item.getPlaceRank());
            meta.put("productType", item.getProductType());
            meta.put("venueName", item.getVenueName());
            meta.put("available", item.getAvailableStock() == null || item.getAvailableStock() > 0);
            meta.put("stock", item.getAvailableStock());
            meta.put("suitableLevel", suitableLevel(item));
            meta.put("sceneTags", sceneTags(item, tags));
            meta.put("pros", pros(item, maxPrice));
            meta.put("cons", cons(item));
            meta.put("recommendScore", productScore(item, maxPrice));
            meta.put("recommendReasons", pros(item, maxPrice).stream().limit(3).toList());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private List<String> sceneTags(VenueItem item, List<String> tags) {
        List<String> sceneTags = new ArrayList<>(tags);
        String type = item.getProductType();
        if ("TIME_PACKAGE".equals(type)) {
            sceneTags.add("低峰畅打");
        } else if ("COURT_SLOT".equals(type)) {
            sceneTags.add("单场预订");
        } else if ("COACH_LESSON".equals(type)) {
            sceneTags.add("私教体验");
        }
        return sceneTags.stream().distinct().limit(6).toList();
    }

    private String suitableLevel(VenueItem item) {
        String type = item.getProductType();
        if ("COACH_LESSON".equals(type)) {
            return "新手/进阶";
        }
        if ("COURT_SLOT".equals(type)) {
            return "双打/固定搭子";
        }
        return "新手/下班快打";
    }

    private List<String> pros(VenueItem item, Integer maxPrice) {
        List<String> pros = new ArrayList<>();
        if (item.getPrice() != null && (maxPrice == null || item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0)) {
            pros.add("价格在预算内");
        }
        if (item.getAvailableStock() == null || item.getAvailableStock() > 0) {
            pros.add("当前可购买");
        }
        if ("TIME_PACKAGE".equals(item.getProductType())) {
            pros.add("低峰时段更适合练球和放松");
        } else if ("COACH_LESSON".equals(item.getProductType())) {
            pros.add("有教练指导，适合纠正动作");
        }
        return pros;
    }

    private List<String> cons(VenueItem item) {
        List<String> cons = new ArrayList<>();
        if (item.getAvailableStock() != null && item.getAvailableStock() <= 3) {
            cons.add("剩余名额较少");
        }
        if (item.getOriginalPrice() == null || item.getOriginalPrice().compareTo(item.getPrice()) <= 0) {
            cons.add("折扣优势不明显");
        }
        if ("COACH_LESSON".equals(item.getProductType())) {
            cons.add("需要按预约时间到场");
        }
        return cons;
    }

    private int productScore(VenueItem item, Integer maxPrice) {
        int score = 50;
        if (item.getPrice() != null) {
            if (maxPrice != null && item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0) {
                score += 25;
            } else if (item.getPrice().compareTo(BigDecimal.valueOf(50)) <= 0) {
                score += 20;
            } else if (item.getPrice().compareTo(BigDecimal.valueOf(100)) <= 0) {
                score += 12;
            }
        }
        if (item.getAvailableStock() == null || item.getAvailableStock() > 5) {
            score += 15;
        } else if (item.getAvailableStock() > 0) {
            score += 8;
        }
        if (item.getOriginalPrice() != null && item.getOriginalPrice().compareTo(item.getPrice()) > 0) {
            score += 10;
        }
        return Math.min(100, score);
    }

    private String yuan(BigDecimal value) {
        return value == null ? null : "¥" + value.stripTrailingZeros().toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToText(String value) {
        return value == null || value.isBlank() ? "可购买项目" : value;
    }
}
