package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.mapper.SocialMapper;
import com.hm.badminton.service.ISocialService;
import com.hm.badminton.service.ISportCatalogService;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SocialService implements ISocialService {

    private final SocialMapper socialMapper;
    private final ISportCatalogService sportCatalogService;

    public SocialService(SocialMapper socialMapper, ISportCatalogService sportCatalogService) {
        this.socialMapper = socialMapper;
        this.sportCatalogService = sportCatalogService;
    }

    public PageResult<PlayerProfile> players(String sportCode, String city, String area, String level,
                                             Double lng, Double lat, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).code();
        }
        String cityText = blankToNull(city);
        String areaText = blankToNull(area);
        String levelText = blankToNull(level);
        Long total = socialMapper.countPlayers(normalizedSport, cityText, areaText, levelText);
        return new PageResult<>(socialMapper.selectPlayers(normalizedSport, cityText, areaText, levelText, lng, lat, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    public PlayerProfile me(Long userId) {
        PlayerProfile profile = socialMapper.selectProfile(userId);
        if (profile == null) {
            throw new BusinessException(404, "球友资料不存在");
        }
        return profile;
    }

    @Transactional
    public void saveProfile(Long userId, ProfileRequest request) {
        socialMapper.updateUserProfileFields(userId, request.city(), request.level());
        socialMapper.upsertProfile(userId, sportCatalogService.require(request.sportCode()).code(), request.city(), request.area(),
                request.longitude(), request.latitude(), request.level(), request.playStyle(), request.availableTime(),
                request.intro(), request.allowInvite());
    }

    public PageResult<SportActivity> activities(String sportCode, String city, String level, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String normalizedSport = null;
        if (!isAllSport(sportCode)) {
            normalizedSport = sportCatalogService.require(sportCode).code();
        }
        String cityText = blankToNull(city);
        String levelText = blankToNull(level);
        Long total = socialMapper.countActivities(normalizedSport, cityText, levelText);
        return new PageResult<>(socialMapper.selectActivities(normalizedSport, cityText, levelText, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    private boolean isAllSport(String sportCode) {
        return sportCode == null || sportCode.isBlank() || "all".equalsIgnoreCase(sportCode);
    }

    @Transactional
    public Long createActivity(Long userId, ActivityRequest request) {
        SocialMapper.InsertActivityRow row = new SocialMapper.InsertActivityRow();
        row.setSportCode(sportCatalogService.require(request.sportCode()).code());
        row.setCreatorId(userId);
        row.setVenueId(request.venueId());
        row.setPlaceSource(request.placeSource() == null || request.placeSource().isBlank() ? "amap" : request.placeSource());
        row.setPlaceId(request.placeId());
        row.setVenueName(resolveVenueName(request));
        row.setTitle(request.title());
        row.setCity(request.city());
        row.setStartTime(request.startTime());
        row.setEndTime(request.endTime());
        row.setMaxPlayers(request.maxPlayers());
        row.setLevelRequired(request.levelRequired());
        row.setFeeType(request.feeType());
        socialMapper.insertActivity(row);
        long activityId = row.getId();
        socialMapper.insertMember(activityId, userId, "发起人");
        return activityId;
    }

    @Transactional
    public void join(Long userId, Long activityId) {
        SportActivity activity = detail(activityId);
        if (!"招募中".equals(activity.status())) {
            throw new BusinessException("活动当前不可加入");
        }
        if (activity.currentPlayers() >= activity.maxPlayers()) {
            throw new BusinessException("活动人数已满");
        }
        try {
            socialMapper.insertMember(activityId, userId, "成员");
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "你已加入该活动");
        }
        socialMapper.increaseActivityPlayers(activityId);
    }

    @Transactional
    public void cancel(Long userId, Long activityId) {
        int deleted = socialMapper.deleteMember(activityId, userId);
        if (deleted > 0) {
            socialMapper.decreaseActivityPlayers(activityId);
        }
    }

    public SportActivity detail(Long id) {
        SportActivity activity = socialMapper.selectActivity(id);
        if (activity == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return activity;
    }

    public List<Map<String, Object>> members(Long activityId) {
        return socialMapper.selectMembers(activityId);
    }

    private String resolveVenueName(ActivityRequest request) {
        if (request.venueName() != null && !request.venueName().isBlank()) {
            return request.venueName().trim();
        }
        if (request.venueId() != null) {
            String venueName = socialMapper.selectVenueName(request.venueId());
            if (venueName != null && !venueName.isBlank()) {
                return venueName;
            }
        }
        return "待定场地";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProfileRequest(
            String sportCode,
            @NotBlank String city,
            @NotBlank String area,
            Double longitude,
            Double latitude,
            @NotBlank String level,
            String playStyle,
            String availableTime,
            String intro,
            Boolean allowInvite) {
    }

    public record ActivityRequest(
            String sportCode,
            Long venueId,
            String placeSource,
            String placeId,
            String venueName,
            @NotBlank String title,
            @NotBlank String city,
            @Future LocalDateTime startTime,
            @Future LocalDateTime endTime,
            @Min(2) Integer maxPlayers,
            @NotBlank String levelRequired,
            String feeType) {
    }
}

