package com.hm.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("player_profiles")
public class PlayerProfileEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long userId;
    private String sportCode;
    private String city;
    private String area;
    private Double longitude;
    private Double latitude;
    private String level;
    private String playStyle;
    private String availableTime;
    private String intro;
    private Boolean allowInvite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

