package com.hm.badminton.service.place.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.config.AmapProperties;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.entity.AmapPlace;
import com.hm.badminton.entity.SportType;
import com.hm.badminton.service.place.IAmapPlaceService;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.utils.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 高德地图 Web 服务适配层。
 *
 * <p>负责真实 POI 附近搜索、自动扩大半径、逆地理编码和 IP 定位。第三方响应先转换成项目自己的
 * {@link AmapPlace}，再交给上层使用；Redis 短缓存用于降低高德调用次数和首屏延迟。</p>
 */
@Service
public class AmapPlaceService implements IAmapPlaceService {

    private static final String AMAP_SOURCE = "amap";
    private static final List<Integer> AUTO_RADIUS_STEPS = List.of(5000, 10000, 20000, 30000);

    private final AmapProperties amapProperties;
    private final ISportCatalogService sportCatalogService;
    private final ObjectMapper objectMapper;
    private final CacheClient cacheClient;
    private final RestClient restClient;

    public AmapPlaceService(AmapProperties amapProperties,
                            ISportCatalogService sportCatalogService,
                            ObjectMapper objectMapper,
                            CacheClient cacheClient) {
        this.amapProperties = amapProperties;
        this.sportCatalogService = sportCatalogService;
        this.objectMapper = objectMapper;
        this.cacheClient = cacheClient;
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
        // 1. 高德附近搜索必须有 Web 服务 key 和经纬度；城市/IP 定位只能作为兜底，不适合精确附近查询。
        if (amapProperties.getKey() == null || amapProperties.getKey().isBlank()) {
            throw new BusinessException(400, "请先配置高德 Web 服务 API Key: 环境变量 AMAP_KEY 或 hm.amap.key");
        }
        if (lng == null || lat == null) {
            throw new BusinessException(422, "附近场所搜索需要提供 lng 和 lat");
        }
        SportType sport = searchSport(sportCode);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), Math.min(Math.max(1, amapProperties.getPageSize()), 25));
        int safeRadius = Math.min(Math.max(radius == null ? amapProperties.getRadius() : radius, 100), 50000);
        // 经纬度按 4 位小数归并到约 10 米网格。相邻滚动、刷新和多个页面组件可复用同一份高德结果。
        String cacheKey = nearbyCacheKey(sport.getCode(), keyword, city, lng, lat, safeRadius, safePage, safeSize);
        return cacheClient.querySimple(cacheKey, new TypeReference<>() {
                }, () -> queryNearbyFromAmap(sport, keyword, city, lng, lat, safeRadius, safePage, safeSize),
                RedisConstants.AMAP_NEARBY_TTL, RedisConstants.AMAP_NEARBY_TTL_JITTER_SECONDS);
    }

    /**
     * 单次附近搜索的真实高德回源逻辑。
     * 结果不足一页时才逐级扩大半径，外层 Redis 短缓存避免相同位置重复触发这段多次回源。
     */
    private PageResult<AmapPlace> queryNearbyFromAmap(SportType sport,
                                                      String keyword,
                                                      String city,
                                                      Double lng,
                                                      Double lat,
                                                      int safeRadius,
                                                      int safePage,
                                                      int safeSize) {
        LinkedHashMap<String, AmapPlace> merged = new LinkedHashMap<>();
        long total = 0;
        // 2. 后端自动扩大半径，直到凑够一页或达到最大半径，避免前端循环请求。
        for (Integer currentRadius : autoRadiusSteps(safeRadius)) {
            PageResult<AmapPlace> result = requestNearby(sport, keyword, city, lng, lat, currentRadius, safePage, safeSize);
            total = Math.max(total, result.getTotal());
            // 3. 用 POI id 去重，保证 5km、10km、20km 的结果合并后不会重复展示。
            for (AmapPlace place : result.getRecords()) {
                merged.putIfAbsent(place.getId(), place);
            }
            if (merged.size() >= safeSize) {
                break;
            }
        }
        // 4. 返回仍按前端请求的 size 截断，total 取高德 total 和合并数量中的较大值。
        return new PageResult<>(merged.values().stream().limit(safeSize).toList(), Math.max(total, merged.size()), safePage, safeSize);
    }

    private PageResult<AmapPlace> requestNearby(SportType sport,
                                                String keyword,
                                                String city,
                                                Double lng,
                                                Double lat,
                                                int radius,
                                                int page,
                                                int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(amapProperties.getEndpoint())
                .queryParam("key", amapProperties.getKey())
                .queryParam("location", lng + "," + lat)
                .queryParam("radius", radius)
                .queryParam("keywords", buildKeywords(sport, keyword))
                .queryParam("page_size", size)
                .queryParam("page_num", page)
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
        // 高德接口里通常 status = "1" 表示返回成功
        if (!"1".equals(status)) {
            String info = root.path("info").asText("unknown");
            String infoCode = root.path("infocode").asText("");
            throw new BusinessException(502, "高德场所搜索失败: " + info + (infoCode.isBlank() ? "" : " (" + infoCode + ")"));
        }

        List<AmapPlace> places = new ArrayList<>();
        JsonNode pois = root.path("pois");
        if (pois.isArray()) {
            int rank = (page - 1) * size + 1;
            for (JsonNode poi : pois) {
                places.add(toPlace(poi, sport, rank++));
            }
        }
        long total = parseLong(root.path("count").asText(), places.size());
        return new PageResult<>(places, total, page, size);
    }

    public Map<String, Object> reverseGeocode(Double lng, Double lat) {
        if (amapProperties.getKey() == null || amapProperties.getKey().isBlank()) {
            throw new BusinessException(400, "请先配置高德 Web 服务 API Key");
        }
        if (lng == null || lat == null) {
            throw new BusinessException(422, "逆地理编码需要提供 lng 和 lat");
        }
        String cacheKey = RedisConstants.AMAP_REGEOCODE_KEY + roundedLocation(lng, lat);
        return cacheClient.querySimple(cacheKey, new TypeReference<>() {
                }, () -> requestReverseGeocode(lng, lat), RedisConstants.AMAP_REGEOCODE_TTL);
    }

    /**
     * IP 定位只用于精确浏览器定位失败时的兜底：
     * 1. IP 归一化并过滤内网地址，避免把 Nginx/本机地址错误传给第三方；
     * 2. 将第三方结果按 IP 短缓存，减少重复调用高德；
     * 3. 高德基础 IP 定位返回的是城市范围，不把它当作用户 GPS，而是取范围中心作为附近搜索中心；
     * 4. 无法识别时才退回默认城市西安。
     */
    @Override
    public Map<String, Object> locateByIp(String clientIp) {
        if (amapProperties.getKey() == null || amapProperties.getKey().isBlank()) {
            return defaultIpLocation();
        }
        String ip = clientIp == null ? "" : clientIp.trim();
        if (ip.isBlank() || isPrivateAddress(ip)) {
            return defaultIpLocation();
        }
        return cacheClient.querySimple(
                RedisConstants.AMAP_IP_LOCATION_KEY + ip,
                new TypeReference<>() {
                },
                () -> requestIpLocation(ip),
                RedisConstants.AMAP_IP_LOCATION_TTL);
    }

    private Map<String, Object> requestIpLocation(String clientIp) {
        URI uri = UriComponentsBuilder.fromUriString(amapProperties.getIpLocationEndpoint())
                .queryParam("key", amapProperties.getKey())
                .queryParam("ip", clientIp)
                .queryParam("output", "JSON")
                .encode()
                .build()
                .toUri();
        try {
            JsonNode root = callAmap(uri);
            if (!"1".equals(root.path("status").asText())) {
                return defaultIpLocation();
            }
            String province = text(root, "province");
            String city = firstNonBlank(text(root, "city"), province, amapProperties.getDefaultCity());
            Double[] center = rectangleCenter(text(root, "rectangle"));
            double lng = center[0] == null ? amapProperties.getDefaultLongitude() : center[0];
            double lat = center[1] == null ? amapProperties.getDefaultLatitude() : center[1];
            return Map.of(
                    "city", city,
                    "district", "",
                    "formattedAddress", city,
                    "shortAddress", city,
                    "lng", lng,
                    "lat", lat,
                    "accuracy", 50000,
                    "source", "ip");
        } catch (RuntimeException ignored) {
            // IP 定位是兜底，第三方异常不影响默认城市场所浏览。
            return defaultIpLocation();
        }
    }

    private Map<String, Object> defaultIpLocation() {
        String city = amapProperties.getDefaultCity();
        return Map.of(
                "city", city,
                "district", "",
                "formattedAddress", city,
                "shortAddress", city,
                "lng", amapProperties.getDefaultLongitude(),
                "lat", amapProperties.getDefaultLatitude(),
                "accuracy", 100000,
                "source", "default");
    }

    private boolean isPrivateAddress(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return true;
        }
    }

    private Double[] rectangleCenter(String rectangle) {
        if (rectangle == null || rectangle.isBlank() || !rectangle.contains(";")) {
            return new Double[]{null, null};
        }
        String[] bounds = rectangle.split(";", 2);
        Double[] southwest = parseLocation(bounds[0]);
        Double[] northeast = parseLocation(bounds[1]);
        if (southwest[0] == null || southwest[1] == null || northeast[0] == null || northeast[1] == null) {
            return new Double[]{null, null};
        }
        return new Double[]{(southwest[0] + northeast[0]) / 2, (southwest[1] + northeast[1]) / 2};
    }

    private Map<String, Object> requestReverseGeocode(Double lng, Double lat) {
        URI uri = UriComponentsBuilder.fromUriString("https://restapi.amap.com/v3/geocode/regeo")
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

    private String roundedLocation(Double lng, Double lat) {
        return String.format(Locale.ROOT, "%.4f,%.4f", lng, lat);
    }

    private String nearbyCacheKey(String sportCode,
                                  String keyword,
                                  String city,
                                  Double lng,
                                  Double lat,
                                  int radius,
                                  int page,
                                  int size) {
        return RedisConstants.AMAP_NEARBY_KEY
                + roundedLocation(lng, lat)
                + ":sport=" + cachePart(sportCode)
                + ":keyword=" + cachePart(keyword)
                + ":city=" + cachePart(city)
                + ":radius=" + radius
                + ":page=" + page
                + ":size=" + size;
    }

    private String cachePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(':', '_');
    }

    private JsonNode callAmap(URI uri) {
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientException e) {
            throw new BusinessException(502, "请求高德地图失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(502, "解析高德地图响应失败");
        }
    }

    private AmapPlace toPlace(JsonNode poi, SportType sport, int rank) {
        SportType actualSport = detectSport(poi, sport);
        String location = text(poi, "location");
        Double[] coordinates = parseLocation(location);
        JsonNode business = poi.path("business");
        String type = text(poi, "type");
        String businessArea = firstNonBlank(text(business, "business_area"), text(poi, "business_area"));
        String openHours = firstNonBlank(text(business, "opentime_week"), text(business, "opentime_today"));
        String coverUrl = firstNonBlank(amapPhotoUrl(poi), localPlaceCoverUrl(rank));
        String id = firstNonBlank(text(poi, "id"), text(poi, "name") + ":" + location);

        return new AmapPlace(
                id,
                text(poi, "name"),
                actualSport.getCode(),
                actualSport.getName(),
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
                tags(actualSport.getName(), type, businessArea),
                AMAP_SOURCE);
    }

    private String buildKeywords(SportType sport, String keyword) {
        if (isAllSport(sport.getCode()) && keyword != null && !keyword.isBlank()) {
            return keyword.trim();
        }
        if (keyword == null || keyword.isBlank()) {
            return String.join("|", sport.getKeywords());
        }
        String trimmed = keyword.trim();
        boolean alreadySportKeyword = sport.getKeywords().stream().anyMatch(trimmed::contains)
                || trimmed.contains(sport.getName());
        return alreadySportKeyword ? trimmed : trimmed + " " + sport.getName();
    }

    private SportType searchSport(String sportCode) {
        if (!isAllSport(sportCode)) {
            return sportCatalogService.require(sportCode);
        }
        List<String> keywords = sportCatalogService.list().stream()
                .flatMap(sport -> sport.getKeywords().stream())
                .distinct()
                .toList();
        return new SportType("all", "全部运动", keywords);
    }

    private SportType detectSport(JsonNode poi, SportType requestedSport) {
        if (!isAllSport(requestedSport.getCode())) {
            return requestedSport;
        }
        String haystack = (text(poi, "name") + " " + text(poi, "type") + " " + text(poi, "address"));
        return sportCatalogService.list().stream()
                .filter(sport -> haystack.contains(sport.getName())
                        || sport.getKeywords().stream().anyMatch(haystack::contains))
                .findFirst()
                .orElse(requestedSport);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    private List<Integer> autoRadiusSteps(int radius) {
        List<Integer> steps = new ArrayList<>();
        steps.add(radius);
        for (Integer step : AUTO_RADIUS_STEPS) {
            if (step > radius && step <= 50000) {
                steps.add(step);
            }
        }
        if (steps.get(steps.size() - 1) < radius) {
            steps.add(radius);
        }
        return steps.stream().distinct().toList();
    }

    private String localPlaceCoverUrl(int rank) {
        int fileId = 31 + Math.floorMod(rank - 1, 25);
        return "/objects/hm-badminton/demo/places/cover/place-%03d.png".formatted(fileId);
    }

    private String amapPhotoUrl(JsonNode poi) {
        JsonNode photos = poi.path("photos");
        if (!photos.isArray() || photos.isEmpty()) {
            return "";
        }
        for (JsonNode photo : photos) {
            String url = firstNonBlank(text(photo, "url"), text(photo, "src"));
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
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



