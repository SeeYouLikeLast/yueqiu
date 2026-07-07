package com.hm.badminton.service.social.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.social.ActivityRequest;
import com.hm.badminton.dto.social.ProfileRequest;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.mapper.social.SocialMapper;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.service.social.ISocialService;
import com.hm.badminton.utils.CacheClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SocialService implements ISocialService {

    private static final String ACTIVITY_RECRUITING = "招募中";
    private static final String ROLE_OWNER = "发起人";
    private static final String ROLE_MEMBER = "成员";

    private final SocialMapper socialMapper;
    private final ISportCatalogService sportCatalogService;
    private final CacheClient cacheClient;

    public SocialService(SocialMapper socialMapper, ISportCatalogService sportCatalogService, CacheClient cacheClient) {
        this.socialMapper = socialMapper;
        this.sportCatalogService = sportCatalogService;
        this.cacheClient = cacheClient;
    }

    @Override
    public PageResult<PlayerProfile> players(String sportCode, String city, String area, String level,
                                             Double lng, Double lat, int page, int size, Long excludeUserId) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = normalizeSport(sportCode);
        String cityText = blankToNull(city);
        String areaText = blankToNull(area);
        String levelText = blankToNull(level);
        String key = RedisConstants.SOCIAL_PLAYERS_KEY
                + listCacheKey(normalizedSport, cityText, areaText, levelText, safePage, safeSize, lng, lat)
                + ":exclude:" + (excludeUserId == null ? "none" : excludeUserId);
        return cacheClient.querySimple(key, new TypeReference<PageResult<PlayerProfile>>() {
        }, () -> {
            Long total = socialMapper.countPlayers(normalizedSport, cityText, areaText, levelText, excludeUserId);
            return new PageResult<>(socialMapper.selectPlayers(normalizedSport, cityText, areaText, levelText, lng, lat, safeSize, (safePage - 1) * safeSize, excludeUserId),
                    total == null ? 0 : total, safePage, safeSize);
        }, RedisConstants.SOCIAL_LIST_TTL);
    }

    @Override
    public PlayerProfile me(Long userId) {
        PlayerProfile profile = socialMapper.selectProfile(userId);
        if (profile == null) {
            throw new BusinessException(404, "球友资料不存在");
        }
        return profile;
    }

    @Override
    @Transactional
    public void saveProfile(Long userId, ProfileRequest request) {
        socialMapper.updateUserProfileFields(userId, request.getCity(), request.getLevel());
        socialMapper.upsertProfile(userId, sportCatalogService.require(request.getSportCode()).getCode(), request.getCity(), request.getArea(),
                request.getLongitude(), request.getLatitude(), request.getLevel(), request.getPlayStyle(), request.getAvailableTime(),
                request.getIntro(), request.getAllowInvite());
    }

    @Override
    public PageResult<SportActivity> activities(String sportCode, String city, String level, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = normalizeSport(sportCode);
        String cityText = blankToNull(city);
        String levelText = blankToNull(level);
        String key = RedisConstants.SOCIAL_ACTIVITIES_KEY + listCacheKey(normalizedSport, cityText, null, levelText, safePage, safeSize, null, null);
        return cacheClient.querySimple(key, new TypeReference<PageResult<SportActivity>>() {
        }, () -> {
            Long total = socialMapper.countActivities(normalizedSport, cityText, levelText);
            return new PageResult<>(socialMapper.selectActivities(normalizedSport, cityText, levelText, safeSize, (safePage - 1) * safeSize),
                    total == null ? 0 : total, safePage, safeSize);
        }, RedisConstants.SOCIAL_LIST_TTL);
    }

    @Override
    @Transactional
    public Long createActivity(Long userId, ActivityRequest request) {
        validateActivityTime(request);
        SocialMapper.InsertActivityRow row = new SocialMapper.InsertActivityRow();
        row.setSportCode(sportCatalogService.require(request.getSportCode()).getCode());
        row.setCreatorId(userId);
        row.setVenueId(request.getVenueId());
        row.setPlaceSource(request.getPlaceSource() == null || request.getPlaceSource().isBlank() ? "amap" : request.getPlaceSource());
        row.setPlaceId(request.getPlaceId());
        row.setVenueName(resolveVenueName(request));
        row.setTitle(request.getTitle());
        row.setCity(request.getCity());
        row.setStartTime(request.getStartTime());
        row.setEndTime(request.getEndTime());
        row.setMaxPlayers(request.getMaxPlayers());
        row.setLevelRequired(request.getLevelRequired());
        row.setFeeType(request.getFeeType());
        socialMapper.insertActivity(row);
        long activityId = row.getId();
        socialMapper.insertMember(activityId, userId, ROLE_OWNER);
        return activityId;
    }

    @Override
    @Transactional
    public void join(Long userId, Long activityId) {
        SportActivity activity = detail(activityId);
        if (!ACTIVITY_RECRUITING.equals(activity.getStatus())) {
            throw new BusinessException("活动已满员或已结束");
        }
        try {
            socialMapper.insertMember(activityId, userId, ROLE_MEMBER);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "你已加入该活动");
        }
        int updated = socialMapper.increaseActivityPlayersIfAvailable(activityId);
        if (updated == 0) {
            throw new BusinessException("活动人数已满");
        }
    }

    private SportActivity detail(Long id) {
        SportActivity activity = socialMapper.selectActivity(id);
        if (activity == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return activity;
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    private String normalizeSport(String sportCode) {
        return isAllSport(sportCode) ? null : sportCatalogService.require(sportCode).getCode();
    }

    private String resolveVenueName(ActivityRequest request) {
        if (request.getVenueName() != null && !request.getVenueName().isBlank()) {
            return request.getVenueName().trim();
        }
        if (request.getVenueId() != null) {
            String venueName = socialMapper.selectVenueName(request.getVenueId());
            if (venueName != null && !venueName.isBlank()) {
                return venueName;
            }
        }
        return "待定场所";
    }

    private void validateActivityTime(ActivityRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("请填写活动开始和结束时间");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!request.getStartTime().isAfter(now)) {
            throw new BusinessException("开始时间不能早于当前时间");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        if (request.getMaxPlayers() == null || request.getMaxPlayers() < 2) {
            throw new BusinessException("活动人数至少为 2 人");
        }
    }

    private String listCacheKey(String sportCode, String city, String area, String level,
                                int page, int size, Double lng, Double lat) {
        return "sport=" + text(sportCode)
                + ":city=" + text(city)
                + ":area=" + text(area)
                + ":level=" + text(level)
                + ":page=" + page
                + ":size=" + size
                + ":lng=" + coordinate(lng)
                + ":lat=" + coordinate(lat);
    }

    private String coordinate(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.3f", value);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
