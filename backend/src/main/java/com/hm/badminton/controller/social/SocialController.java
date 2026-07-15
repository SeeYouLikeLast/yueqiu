package com.hm.badminton.controller.social;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.social.ActivityRequest;
import com.hm.badminton.dto.social.ProfileRequest;
import com.hm.badminton.dto.social.VenueActivityBookingRequest;
import com.hm.badminton.dto.social.VenueActivityBookingResult;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.social.ISocialService;
import com.hm.badminton.service.social.IVenueActivityBookingService;
import com.hm.badminton.utils.LocationContextResolver;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/social")
public class SocialController {

    private final ISocialService socialService;
    private final IVenueActivityBookingService venueActivityBookingService;
    private final UserContext userContext;
    private final LocationContextResolver locationContextResolver;

    public SocialController(ISocialService socialService,
                            IVenueActivityBookingService venueActivityBookingService,
                            UserContext userContext,
                            LocationContextResolver locationContextResolver) {
        this.socialService = socialService;
        this.venueActivityBookingService = venueActivityBookingService;
        this.userContext = userContext;
        this.locationContextResolver = locationContextResolver;
    }

    @GetMapping("/players")
    public ApiResponse<PageResult<PlayerProfile>> players(@RequestParam(required = false) String sport,
                                                          @RequestHeader(value = "X-Location-City", required = false) String locationCity,
                                                          @RequestParam(required = false) String area,
                                                          @RequestParam(required = false) String level,
                                                          @RequestParam(required = false) Double lng,
                                                          @RequestParam(required = false) Double lat,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "12") int size) {
        LoginUser user = userContext.current().orElse(null);
        return ApiResponse.ok(socialService.players(
                sport,
                locationContextResolver.resolveCity(locationCity),
                area,
                level,
                lng == null && user != null ? user.getLongitude() : lng,
                lat == null && user != null ? user.getLatitude() : lat,
                page,
                size,
                user == null ? null : user.getId()));
    }

    @GetMapping("/profile/me")
    public ApiResponse<PlayerProfile> myProfile() {
        return ApiResponse.ok(socialService.me(userContext.requireUserId()));
    }

    @PostMapping("/profile/me")
    public ApiResponse<Void> saveProfile(@Valid @RequestBody ProfileRequest request) {
        socialService.saveProfile(userContext.requireUserId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<SportActivity>> activities(@RequestParam(required = false) String sport,
                                                             @RequestHeader(value = "X-Location-City", required = false) String locationCity,
                                                             @RequestParam(required = false) String level,
                                                             @RequestParam(defaultValue = "others") String scope,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "12") int size) {
        LoginUser user = userContext.current().orElse(null);
        return ApiResponse.ok(socialService.activities(
                sport,
                locationContextResolver.resolveCity(locationCity),
                level,
                user == null ? null : user.getId(),
                scope,
                page,
                size));
    }

    @PostMapping("/activities")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(Map.of("activityId", socialService.createActivity(userContext.requireUserId(), request)));
    }

    /** 用户确认后购买指定场馆时段，并在同一时段发起约球活动。 */
    @PostMapping("/activities/book-and-create")
    public ApiResponse<VenueActivityBookingResult> bookAndCreate(@Valid @RequestBody VenueActivityBookingRequest request) {
        return ApiResponse.ok(venueActivityBookingService.bookAndCreateActivity(userContext.requireUserId(), request));
    }

    @PostMapping("/activities/{id}/join")
    public ApiResponse<Void> join(@PathVariable Long id) {
        socialService.join(userContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}



