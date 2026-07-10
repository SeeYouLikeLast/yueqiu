package com.hm.badminton.service.place;

import com.hm.badminton.entity.VenueReview;

import java.util.List;

public interface IVenueService {
    List<VenueReview> reviews(Long venueId, int page, int size);

    List<VenueReview> reviewsByPlaceSlot(String city, String sportCode, int placeRank, int page, int size);
}


