package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SportActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long creatorId;
    private String creatorName;
    private String sportCode;
    private Long venueId;
    private String placeSource;
    private String placeId;
    private String venueName;
    private String title;
    private String city;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxPlayers;
    private Integer currentPlayers;
    private String levelRequired;
    private String feeType;
    private String status;
    private LocalDateTime createdAt;
}

