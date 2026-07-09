package com.hm.badminton.service.agent.tools;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.AmapPlace;
import com.hm.badminton.service.place.IAmapPlaceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlaceAgentTool {

    private final IAmapPlaceService amapPlaceService;

    public PlaceAgentTool(IAmapPlaceService amapPlaceService) {
        this.amapPlaceService = amapPlaceService;
    }

    @Tool(name = "searchNearbyPlaces", description = "Search real nearby sports places by sport, city and location.")
    public List<AgentCard> searchNearbyPlaces(String sportCode,
                                              String city,
                                              Double lng,
                                              Double lat,
                                              Integer radius,
                                              String keyword) {
        if (lng == null || lat == null) {
            lng = 108.946465;
            lat = 34.347269;
        }
        PageResult<AmapPlace> page = amapPlaceService.nearby(
                blankToNull(sportCode),
                blankToNull(keyword),
                blankToNull(city),
                lng,
                lat,
                radius == null ? 8000 : radius,
                1,
                6);
        List<AgentCard> cards = new ArrayList<>();
        int rank = 1;
        for (AmapPlace place : page.getRecords()) {
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_PLACE);
            card.setTitle(place.getName());
            card.setSubtitle(place.getAddress());
            card.setCoverUrl(place.getCoverUrl());
            card.setTags(List.of(place.getSportName(), distanceText(place.getDistanceMeters()), nullToText(place.getArea())));
            card.setAction(AgentAction.of(AgentConstants.ACTION_OPEN_PLACE, place.getId()));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", place.getId());
            meta.put("sportCode", place.getSportCode());
            meta.put("sportName", place.getSportName());
            meta.put("city", place.getCity());
            meta.put("area", place.getArea());
            meta.put("lng", place.getLongitude());
            meta.put("lat", place.getLatitude());
            meta.put("distanceMeters", place.getDistanceMeters());
            meta.put("placeRank", rank++);
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private String distanceText(Double distanceMeters) {
        if (distanceMeters == null) {
            return "附近";
        }
        if (distanceMeters >= 1000) {
            return String.format("%.1fkm", distanceMeters / 1000);
        }
        return Math.round(distanceMeters) + "m";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToText(String value) {
        return value == null || value.isBlank() ? "本地场所" : value;
    }
}
