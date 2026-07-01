package com.hm.badminton.service;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.Venue;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.service.impl.VenueService;

import java.util.List;
import java.util.Map;

public interface IVenueService {
    PageResult<Venue> list(String city, String area, String keyword, Double lng, Double lat, int page, int size);

    Venue detail(Long id);

    List<Map<String, Object>> courts(Long venueId);

    List<Map<String, Object>> timeSlots(Long venueId);

    List<VenueReview> reviews(Long venueId, int page, int size);

    VenueReview createReview(Long userId, VenueService.ReviewRequest request);

    boolean toggleFavorite(Long userId, Long venueId);

    List<Venue> favorites(Long userId);

    Long createVenue(VenueService.VenueCreateRequest request);
}
