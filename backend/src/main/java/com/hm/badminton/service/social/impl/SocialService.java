package com.hm.badminton.service.social.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.social.ActivityRequest;
import com.hm.badminton.dto.social.ProfileRequest;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.mapper.social.SocialMapper;
import com.hm.badminton.service.catalog.ISportCatalogService;
import com.hm.badminton.service.social.ISocialService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialService implements ISocialService {

    private static final String ACTIVITY_RECRUITING = "招募中";
    private static final String ROLE_OWNER = "发起人";
    private static final String ROLE_MEMBER = "成员";

    private final SocialMapper socialMapper;
    private final ISportCatalogService sportCatalogService;

    public SocialService(SocialMapper socialMapper, ISportCatalogService sportCatalogService) {
        this.socialMapper = socialMapper;
        this.sportCatalogService = sportCatalogService;
    }

    @Override
    public PageResult<PlayerProfile> players(String sportCode, String city, String area, String level,
                                             Double lng, Double lat, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).getCode();
        }
        String cityText = blankToNull(city);
        String areaText = blankToNull(area);
        String levelText = blankToNull(level);
        Long total = socialMapper.countPlayers(normalizedSport, cityText, areaText, levelText);
        return new PageResult<>(socialMapper.selectPlayers(normalizedSport, cityText, areaText, levelText, lng, lat, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
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
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).getCode();
        }
        String cityText = blankToNull(city);
        String levelText = blankToNull(level);
        Long total = socialMapper.countActivities(normalizedSport, cityText, levelText);
        return new PageResult<>(socialMapper.selectActivities(normalizedSport, cityText, levelText, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    @Override
    @Transactional
    public Long createActivity(Long userId, ActivityRequest request) {
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
        if (activity.getCurrentPlayers() >= activity.getMaxPlayers()) {
            throw new BusinessException("活动人数已满");
        }
        try {
            socialMapper.insertMember(activityId, userId, ROLE_MEMBER);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "你已加入该活动");
        }
        socialMapper.increaseActivityPlayers(activityId);
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
