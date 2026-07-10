package com.hm.badminton.service.catalog.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.entity.SportType;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.utils.CacheClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SportCatalogService implements ISportCatalogService {

    private final Map<String, SportType> sports = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final CacheClient cacheClient;

    public SportCatalogService(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
        register(new SportType("badminton", "羽毛球", List.of("羽毛球馆", "羽毛球场", "羽毛球")));
        register(new SportType("table_tennis", "乒乓球", List.of("乒乓球馆", "乒乓球室", "乒乓球")));
        register(new SportType("football", "足球", List.of("足球场", "足球俱乐部", "足球")));
        register(new SportType("basketball", "篮球", List.of("篮球馆", "篮球场", "篮球")));
        register(new SportType("tennis", "网球", List.of("网球场", "网球馆", "网球")));
        register(new SportType("volleyball", "排球", List.of("排球馆", "排球场", "排球")));

        alias("pingpong", "table_tennis");
        alias("ping_pong", "table_tennis");
        alias("tabletennis", "table_tennis");
        alias("soccer", "football");
    }

    public List<SportType> list() {
        return cacheClient.querySimple(
                RedisConstants.SPORT_LIST_KEY,
                new TypeReference<>() {
                },
                this::snapshot,
                RedisConstants.SPORT_LIST_TTL);
    }

    public SportType require(String code) {
        String normalized = normalize(code);
        SportType sport = sports.get(normalized);
        if (sport == null) {
            throw new BusinessException(422, "暂不支持该运动类型: " + code);
        }
        return sport;
    }

    public String normalize(String code) {
        if (code == null || code.isBlank()) {
            return "badminton";
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return aliases.getOrDefault(normalized, normalized);
    }

    private void register(SportType sportType) {
        sports.put(sportType.getCode(), sportType);
    }

    private List<SportType> snapshot() {
        return new ArrayList<>(sports.values());
    }

    private void alias(String alias, String code) {
        aliases.put(alias, code);
    }
}
