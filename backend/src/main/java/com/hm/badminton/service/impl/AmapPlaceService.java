package com.hm.badminton.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.config.AmapProperties;
import com.hm.badminton.entity.AmapPlace;
import com.hm.badminton.entity.SportType;
import com.hm.badminton.service.IAmapPlaceService;
import com.hm.badminton.service.ISportCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AmapPlaceService implements IAmapPlaceService {

    private static final String AMAP_SOURCE = "amap";

    private final AmapProperties amapProperties;
    private final ISportCatalogService sportCatalogService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AmapPlaceService(AmapProperties amapProperties,
                            ISportCatalogService sportCatalogService,
                            ObjectMapper objectMapper) {
        this.amapProperties = amapProperties;
        this.sportCatalogService = sportCatalogService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public PageResult<AmapPlace> nearby(String sportCode,
                                        String keyword,
                                        String city,
                                        Double lng,
                                        Double lat,
                                        Integer radius,
                                        int page,
                                        int size) {
        if (amapProperties.getKey() == null || amapProperties.getKey().isBlank()) {
            throw new BusinessException(400, "请先配置高德 Web服务 API Key：环境变量 AMAP_KEY 或 hm.amap.key");
        }
        if (lng == null || lat == null) {
            throw new BusinessException(422, "附近场所搜索需要提供 lng 和 lat");
        }
        SportType sport = searchSport(sportCode);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), Math.min(Math.max(1, amapProperties.getPageSize()), 25));
        int safeRadius = Math.min(Math.max(radius == null ? amapProperties.getRadius() : radius, 100), 50000);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(amapProperties.getEndpoint())
                .queryParam("key", amapProperties.getKey())
                .queryParam("location", lng + "," + lat)
                .queryParam("radius", safeRadius)
                .queryParam("keywords", buildKeywords(sport, keyword))
                .queryParam("page_size", safeSize)
                .queryParam("page_num", safePage)
                .queryParam("show_fields", "business,photos");
        if (city != null && !city.isBlank()) {
            builder.queryParam("region", city.trim());
        }

        URI uri = builder
                .encode()
                .build()
                .toUri();

        JsonNode root = callAmap(uri);
        String status = root.path("status").asText();
        if (!"1".equals(status)) {
            throw new BusinessException(502, "高德场所搜索失败：" + root.path("info").asText("未知错误"));
        }

        List<AmapPlace> places = new ArrayList<>();
        JsonNode pois = root.path("pois");
        if (pois.isArray()) {
            for (JsonNode poi : pois) {
                places.add(toPlace(poi, sport));
            }
        }
        long total = parseLong(root.path("count").asText(), places.size());
        return new PageResult<>(places, total, safePage, safeSize);
    }

    public Map<String, Object> reverseGeocode(Double lng, Double lat) {
        if (amapProperties.getKey() == null || amapProperties.getKey().isBlank()) {
            throw new BusinessException(400, "请先配置高德 Web 服务 API Key");
        }
        if (lng == null || lat == null) {
            throw new BusinessException(422, "逆地理编码需要提供 lng 和 lat");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/geocode/regeo")
                .queryParam("key", amapProperties.getKey())
                .queryParam("location", lng + "," + lat)
                .queryParam("extensions", "base")
                .queryParam("radius", "1000")
                .encode()
                .build()
                .toUri();
        JsonNode root = callAmap(uri);
        if (!"1".equals(root.path("status").asText())) {
            throw new BusinessException(502, "高德逆地理编码失败：" + root.path("info").asText("未知错误"));
        }
        JsonNode regeocode = root.path("regeocode");
        JsonNode component = regeocode.path("addressComponent");
        String province = text(component, "province");
        String city = firstNonBlank(text(component, "city"), province);
        String district = text(component, "district");
        String township = text(component, "township");
        JsonNode streetNumber = component.path("streetNumber");
        String street = text(streetNumber, "street");
        String number = text(streetNumber, "number");
        String shortAddress = List.of(district, township, street, number).stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce("", String::concat);
        return Map.of(
                "city", city,
                "district", district,
                "formattedAddress", text(regeocode, "formatted_address"),
                "shortAddress", shortAddress.isBlank() ? text(regeocode, "formatted_address") : shortAddress
        );
    }

    private JsonNode callAmap(URI uri) {
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientException e) {
            throw new BusinessException(502, "请求高德场所搜索失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(502, "解析高德场所搜索响应失败");
        }
    }

    private AmapPlace toPlace(JsonNode poi, SportType sport) {
        SportType actualSport = detectSport(poi, sport);
        String location = text(poi, "location");
        Double[] coordinates = parseLocation(location);
        JsonNode business = poi.path("business");
        String type = text(poi, "type");
        String businessArea = firstNonBlank(text(business, "business_area"), text(poi, "business_area"));
        String openHours = firstNonBlank(text(business, "opentime_week"), text(business, "opentime_today"));
        String coverUrl = firstPhotoUrl(poi.path("photos"));
        String id = firstNonBlank(text(poi, "id"), text(poi, "name") + ":" + location);

        return new AmapPlace(
                id,
                text(poi, "name"),
                actualSport.code(),
                actualSport.name(),
                text(poi, "cityname"),
                text(poi, "adname"),
                text(poi, "address"),
                coordinates[0],
                coordinates[1],
                parseDouble(text(poi, "distance")),
                type,
                text(poi, "tel"),
                businessArea,
                openHours,
                coverUrl,
                tags(actualSport.name(), type, businessArea),
                AMAP_SOURCE);
    }

    private String buildKeywords(SportType sport, String keyword) {
        if (isAllSport(sport.code()) && keyword != null && !keyword.isBlank()) {
            return keyword.trim();
        }
        if (keyword == null || keyword.isBlank()) {
            return String.join("|", sport.keywords());
        }
        String trimmed = keyword.trim();
        boolean alreadySportKeyword = sport.keywords().stream().anyMatch(trimmed::contains)
                || trimmed.contains(sport.name());
        return alreadySportKeyword ? trimmed : trimmed + " " + sport.name();
    }

    private SportType searchSport(String sportCode) {
        if (!isAllSport(sportCode)) {
            return sportCatalogService.require(sportCode);
        }
        List<String> keywords = sportCatalogService.list().stream()
                .flatMap(sport -> sport.keywords().stream())
                .distinct()
                .toList();
        return new SportType("all", "全部运动", keywords);
    }

    private SportType detectSport(JsonNode poi, SportType requestedSport) {
        if (!isAllSport(requestedSport.code())) {
            return requestedSport;
        }
        String haystack = (text(poi, "name") + " " + text(poi, "type") + " " + text(poi, "address"));
        return sportCatalogService.list().stream()
                .filter(sport -> haystack.contains(sport.name())
                        || sport.keywords().stream().anyMatch(haystack::contains))
                .findFirst()
                .orElse(requestedSport);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    private String firstPhotoUrl(JsonNode photos) {
        if (!photos.isArray() || photos.isEmpty()) {
            return "";
        }
        return text(photos.get(0), "url");
    }

    private List<String> tags(String... values) {
        List<String> tags = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                tags.add(value);
            }
        }
        return tags;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (value.isArray()) {
            return value.isEmpty() ? "" : value.get(0).asText("");
        }
        if (value.isObject()) {
            return "";
        }
        return value.asText("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Double[] parseLocation(String location) {
        if (location == null || location.isBlank() || !location.contains(",")) {
            return new Double[]{null, null};
        }
        String[] parts = location.split(",", 2);
        return new Double[]{parseDouble(parts[0]), parseDouble(parts[1])};
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}

