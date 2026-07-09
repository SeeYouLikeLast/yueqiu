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
            card.setTags(item.getTags() == null ? List.of(item.getProductTypeName()) : item.getTags());
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
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private String yuan(BigDecimal value) {
        return value == null ? null : "¥" + value.stripTrailingZeros().toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
