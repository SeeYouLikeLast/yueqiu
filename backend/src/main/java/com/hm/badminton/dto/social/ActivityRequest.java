package com.hm.badminton.dto.social;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRequest {
    private String sportCode;
    private Long venueId;
    private String placeSource;
    private String placeId;
    private String venueName;

    @NotBlank
    private String title;

    @NotBlank
    private String city;

    @Future
    private LocalDateTime startTime;

    @Future
    private LocalDateTime endTime;

    @Min(2)
    @Max(20)
    private Integer maxPlayers;

    @NotBlank
    private String levelRequired;

    private String feeType;
}
