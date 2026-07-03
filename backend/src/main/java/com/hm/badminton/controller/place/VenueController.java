package com.hm.badminton.controller.place;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.service.place.IVenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/venues")
public class VenueController {

    private final IVenueService venueService;

    public VenueController(IVenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<List<VenueReview>> reviews(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(venueService.reviews(id, page, size));
    }

}



