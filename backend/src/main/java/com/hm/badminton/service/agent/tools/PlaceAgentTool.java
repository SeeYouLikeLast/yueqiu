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
            int currentRank = rank++;
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_PLACE);
            card.setTitle(place.getName());
            card.setSubtitle(place.getAddress());
            card.setCoverUrl(place.getCoverUrl());
            card.setTags(placeTags(place));
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
            meta.put("available", true);
            meta.put("sceneTags", sceneTags(place));
            meta.put("pros", pros(place));
            meta.put("cons", cons(place));
            meta.put("recommendScore", placeScore(place, currentRank));
            meta.put("recommendReasons", pros(place).stream().limit(3).toList());
            meta.put("placeRank", currentRank);
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private List<String> placeTags(AmapPlace place) {
        List<String> tags = new ArrayList<>();
        tags.add(nullToText(place.getSportName()));
        tags.add(distanceText(place.getDistanceMeters()));
        tags.add(nullToText(place.getArea()));
        return tags;
    }

    private List<String> sceneTags(AmapPlace place) {
        List<String> tags = new ArrayList<>();
        if (place.getFacilities() != null) {
            tags.addAll(place.getFacilities().stream().filter(value -> value != null && !value.isBlank()).limit(4).toList());
        }
        if (place.getOpenHours() != null && !place.getOpenHours().isBlank()) {
            tags.add("营业 " + place.getOpenHours());
        }
        if (place.getDistanceMeters() != null && place.getDistanceMeters() <= 1500) {
            tags.add("近距离");
        }
        return tags;
    }

    private List<String> pros(AmapPlace place) {
        List<String> pros = new ArrayList<>();
        Double distance = place.getDistanceMeters();
        if (distance != null) {
            if (distance <= 1000) {
                pros.add("距离很近，适合下班后快速过去");
            } else if (distance <= 3000) {
                pros.add("距离适中，通勤压力不大");
            }
        }
        if (place.getFacilities() != null && !place.getFacilities().isEmpty()) {
            pros.add("设施标签清楚：" + String.join("、", place.getFacilities().stream().limit(3).toList()));
        }
        if (place.getOpenHours() != null && place.getOpenHours().contains("22")) {
            pros.add("营业到晚上，适合晚间运动");
        }
        if (pros.isEmpty()) {
            pros.add("附近可达，适合作为备选场所");
        }
        return pros;
    }

    private List<String> cons(AmapPlace place) {
        List<String> cons = new ArrayList<>();
        Double distance = place.getDistanceMeters();
        if (distance != null && distance > 5000) {
            cons.add("距离偏远，需要预留路上时间");
        }
        if (place.getFacilities() == null || place.getFacilities().isEmpty()) {
            cons.add("设施信息较少，建议进详情再确认");
        }
        return cons;
    }

    private int placeScore(AmapPlace place, int rank) {
        int score = 55;
        Double distance = place.getDistanceMeters();
        if (distance != null) {
            if (distance <= 1000) {
                score += 25;
            } else if (distance <= 3000) {
                score += 18;
            } else if (distance <= 6000) {
                score += 10;
            } else {
                score += 4;
            }
        }
        if (place.getFacilities() != null) {
            score += Math.min(10, place.getFacilities().size() * 2);
        }
        if (place.getOpenHours() != null && place.getOpenHours().contains("22")) {
            score += 5;
        }
        score += Math.max(0, 6 - rank) * 2;
        return Math.min(100, score);
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
