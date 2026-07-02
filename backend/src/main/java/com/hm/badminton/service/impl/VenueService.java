package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.Venue;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.mapper.VenueMapper;
import com.hm.badminton.service.IVenueService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class VenueService implements IVenueService {

    private final VenueMapper venueMapper;

    public VenueService(VenueMapper venueMapper) {
        this.venueMapper = venueMapper;
    }

    public PageResult<Venue> list(String city, String area, String keyword, Double lng, Double lat, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String cityText = blankToNull(city);
        String areaText = blankToNull(area);
        String keywordText = blankToNull(keyword);
        Long total = venueMapper.countVenues(cityText, areaText, keywordText);
        return new PageResult<>(venueMapper.selectVenues(cityText, areaText, keywordText, lng, lat, safeSize, (safePage - 1) * safeSize),
                total == null ? 0 : total, safePage, safeSize);
    }

    @Cacheable(value = "venue", key = "#id")
    public Venue detail(Long id) {
        Venue venue = venueMapper.selectVenue(id);
        if (venue == null) {
            throw new BusinessException(404, "场馆不存在");
        }
        return venue;
    }

    public List<Map<String, Object>> courts(Long venueId) {
        return venueMapper.selectCourts(venueId);
    }

    public List<Map<String, Object>> timeSlots(Long venueId) {
        return venueMapper.selectTimeSlots(venueId);
    }

    public List<VenueReview> reviews(Long venueId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return venueMapper.selectReviews(venueId, safeSize, (safePage - 1) * safeSize);
    }

    @Transactional
    @CacheEvict(value = "venue", key = "#request.getVenueId()")
    public VenueReview createReview(Long userId, ReviewRequest request) {
        detail(request.getVenueId());
        venueMapper.insertReview(userId, request.getVenueId(), request.getRating(), request.getContent(), request.getImageUrls());
        venueMapper.refreshVenueRating(request.getVenueId());
        return reviews(request.getVenueId(), 1, 1).getFirst();
    }

    public boolean toggleFavorite(Long userId, Long venueId) {
        Integer exists = venueMapper.countFavorite(userId, venueId);
        if (exists != null && exists > 0) {
            venueMapper.deleteFavorite(userId, venueId);
            return false;
        }
        venueMapper.insertFavorite(userId, venueId);
        return true;
    }

    public List<Venue> favorites(Long userId) {
        return venueMapper.selectFavorites(userId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        private Long venueId;
        @Min(1)
        @Max(5)
        private Integer rating;
        @NotBlank
        private String content;
        private String imageUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueCreateRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String city;
        @NotBlank
        private String area;
        @NotBlank
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer avgPrice;
        private String openHours;
        private String coverUrl;
        private String facilities;
    }

    @Transactional
    public Long createVenue(VenueCreateRequest request) {
        VenueMapper.InsertVenueRow row = new VenueMapper.InsertVenueRow();
        row.setName(request.getName());
        row.setCity(request.getCity());
        row.setArea(request.getArea());
        row.setAddress(request.getAddress());
        row.setLongitude(request.getLongitude());
        row.setLatitude(request.getLatitude());
        row.setAvgPrice(request.getAvgPrice() == null ? 60 : request.getAvgPrice());
        row.setOpenHours(request.getOpenHours());
        row.setCoverUrl(request.getCoverUrl());
        row.setFacilities(request.getFacilities());
        venueMapper.insertVenue(row);
        return row.getId();
    }
}

