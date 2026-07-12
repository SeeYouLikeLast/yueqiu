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
import java.util.Comparator;
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
        return searchVenueProducts(sportCode, placeRank, productType, maxPrice, null);
    }

    public List<AgentCard> searchVenueProducts(String sportCode,
                                               Integer placeRank,
                                               String productType,
                                               Integer maxPrice,
                                               String timePreference) {
        List<VenueItem> items = placeRank == null
                ? venueItemService.saleItems(blankToNull(sportCode), blankToNull(productType), null, 1, 6).getRecords()
                : venueItemService.items(blankToNull(sportCode), null, null, placeRank, 6);
        List<AgentCard> cards = new ArrayList<>();
        List<VenueItem> candidates = candidateItems(items, maxPrice, timePreference);
        boolean hasTimedMatch = hasTimePreference(timePreference)
                && items.stream().anyMatch(item -> timeScore(item, timePreference) > 0);
        for (VenueItem item : candidates) {
            if (!hasTimedMatch && maxPrice != null && item.getPrice() != null
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
            meta.put("timePreference", blankToNull(timePreference));
            meta.put("timeMatch", timeMatchText(item, timePreference));
            meta.put("suitableLevel", suitableLevel(item));
            meta.put("sceneTags", sceneTags(item, tags));
            meta.put("pros", pros(item, maxPrice));
            meta.put("cons", cons(item));
            meta.put("recommendScore", productScore(item, maxPrice, timePreference));
            meta.put("recommendReasons", pros(item, maxPrice).stream().limit(3).toList());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private List<VenueItem> candidateItems(List<VenueItem> items, Integer maxPrice, String timePreference) {
        List<VenueItem> sorted = sortByTimeAndScore(items, maxPrice, timePreference);
        if (!hasTimePreference(timePreference)) {
            return sorted;
        }
        List<VenueItem> matched = sorted.stream()
                .filter(item -> timeScore(item, timePreference) > 0)
                .toList();
        if (matched.isEmpty()) {
            return sorted;
        }
        List<VenueItem> matchedWithinBudget = matched.stream()
                .filter(item -> maxPrice == null || item.getPrice() == null
                        || item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0)
                .toList();
        return matchedWithinBudget.isEmpty() ? matched : matchedWithinBudget;
    }

    private List<VenueItem> sortByTimeAndScore(List<VenueItem> items, Integer maxPrice, String timePreference) {
        return items.stream()
                .sorted(Comparator.comparingInt((VenueItem item) -> productScore(item, maxPrice, timePreference)).reversed()
                        .thenComparing(VenueItem::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VenueItem::getId))
                .toList();
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

    private int productScore(VenueItem item, Integer maxPrice, String timePreference) {
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
        score += timeScore(item, timePreference);
        return Math.min(100, score);
    }

    private int timeScore(VenueItem item, String timePreference) {
        String type = item.getProductType() == null ? "" : item.getProductType();
        String text = ((item.getTitle() == null ? "" : item.getTitle()) + " "
                + (item.getDescription() == null ? "" : item.getDescription()) + " "
                + (item.getUseRule() == null ? "" : item.getUseRule()));
        if ("EVENING".equals(timePreference)) {
            if (containsAny(text, "晚间", "晚上", "夜场", "18:00", "19:00", "20:00", "21:00", "黄金")) {
                return 35;
            }
            if ("COURT_SLOT".equals(type)) {
                return 25;
            }
            if (containsAny(text, "08:00-12:00", "上午", "早场", "低峰")) {
                return -35;
            }
        }
        if ("MORNING".equals(timePreference)) {
            if (containsAny(text, "08:00-12:00", "上午", "早场")) {
                return 35;
            }
            if ("COURT_SLOT".equals(type)) {
                return 15;
            }
        }
        if ("AFTERNOON".equals(timePreference)) {
            if (containsAny(text, "下午", "14:00", "15:00", "16:00", "17:00")) {
                return 35;
            }
            if ("COURT_SLOT".equals(type)) {
                return 25;
            }
            if (containsAny(text, "08:00-12:00", "上午", "早场")) {
                return -20;
            }
        }
        return 0;
    }

    private String timeMatchText(VenueItem item, String timePreference) {
        if ("EVENING".equals(timePreference)) {
            return timeScore(item, timePreference) > 0
                    ? "更接近晚间需求"
                    : "不是晚间券，优先看可预约单场";
        }
        if ("MORNING".equals(timePreference)) {
            return timeScore(item, timePreference) > 0
                    ? "更接近上午需求"
                    : "未明确覆盖上午，需看库存时段";
        }
        if ("AFTERNOON".equals(timePreference)) {
            return timeScore(item, timePreference) > 0
                    ? "更接近下午需求"
                    : "未明确覆盖下午，优先看单场";
        }
        return null;
    }

    private boolean hasTimePreference(String timePreference) {
        return timePreference != null && !timePreference.isBlank();
    }

    private boolean containsAny(String text, String... keywords) {
        String value = text == null ? "" : text;
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
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
