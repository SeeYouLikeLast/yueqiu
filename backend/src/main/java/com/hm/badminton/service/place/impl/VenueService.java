package com.hm.badminton.service.place.impl;

import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.mapper.place.VenueMapper;
import com.hm.badminton.service.place.IVenueService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VenueService implements IVenueService {

    private final VenueMapper venueMapper;

    public VenueService(VenueMapper venueMapper) {
        this.venueMapper = venueMapper;
    }

    @Override
    public List<VenueReview> reviews(Long venueId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return venueMapper.selectReviews(venueId, safeSize, (safePage - 1) * safeSize);
    }
}
