package com.hm.badminton.mapper;

import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SocialMapper {

    @Select("""
            <script>
            select count(*)
            from player_profiles pp
            join users u on u.id = pp.user_id
            where pp.allow_invite = 1 and u.status = 1
            <if test="sportCode != null and sportCode != ''">and pp.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and pp.city = #{city}</if>
            <if test="area != null and area != ''">and pp.area = #{area}</if>
            <if test="level != null and level != ''">and pp.level = #{level}</if>
            </script>
            """)
    Long countPlayers(@Param("sportCode") String sportCode,
                      @Param("city") String city,
                      @Param("area") String area,
                      @Param("level") String level);

    @Select("""
            <script>
            select pp.user_id, u.nickname, u.avatar, pp.sport_code, pp.city, pp.area, pp.longitude, pp.latitude,
                   pp.level, pp.play_style, pp.available_time, pp.intro, pp.allow_invite,
                   <choose>
                     <when test="lng != null and lat != null">
                       (6371000 * acos(least(1, greatest(-1,
                         cos(radians(#{lat})) * cos(radians(pp.latitude)) * cos(radians(pp.longitude) - radians(#{lng})) +
                         sin(radians(#{lat})) * sin(radians(pp.latitude))
                       )))) as distance_m
                     </when>
                     <otherwise>null as distance_m</otherwise>
                   </choose>
            from player_profiles pp
            join users u on u.id = pp.user_id
            where pp.allow_invite = 1 and u.status = 1
            <if test="sportCode != null and sportCode != ''">and pp.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and pp.city = #{city}</if>
            <if test="area != null and area != ''">and pp.area = #{area}</if>
            <if test="level != null and level != ''">and pp.level = #{level}</if>
            <choose>
              <when test="lng != null and lat != null">order by distance_m asc</when>
              <otherwise>order by pp.updated_at desc</otherwise>
            </choose>
            limit #{size} offset #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "area", javaType = String.class),
            @Arg(column = "longitude", javaType = Double.class),
            @Arg(column = "latitude", javaType = Double.class),
            @Arg(column = "level", javaType = String.class),
            @Arg(column = "play_style", javaType = String.class),
            @Arg(column = "available_time", javaType = String.class),
            @Arg(column = "intro", javaType = String.class),
            @Arg(column = "allow_invite", javaType = Boolean.class),
            @Arg(column = "distance_m", javaType = Double.class)
    })
    List<PlayerProfile> selectPlayers(@Param("sportCode") String sportCode,
                                      @Param("city") String city,
                                      @Param("area") String area,
                                      @Param("level") String level,
                                      @Param("lng") Double lng,
                                      @Param("lat") Double lat,
                                      @Param("size") int size,
                                      @Param("offset") int offset);

    @Select("""
            select pp.user_id, u.nickname, u.avatar, pp.sport_code, pp.city, pp.area, pp.longitude, pp.latitude,
                   pp.level, pp.play_style, pp.available_time, pp.intro, pp.allow_invite,
                   null as distance_m
            from player_profiles pp
            join users u on u.id = pp.user_id
            where pp.user_id = #{userId}
            """)
    @ConstructorArgs({
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "area", javaType = String.class),
            @Arg(column = "longitude", javaType = Double.class),
            @Arg(column = "latitude", javaType = Double.class),
            @Arg(column = "level", javaType = String.class),
            @Arg(column = "play_style", javaType = String.class),
            @Arg(column = "available_time", javaType = String.class),
            @Arg(column = "intro", javaType = String.class),
            @Arg(column = "allow_invite", javaType = Boolean.class),
            @Arg(column = "distance_m", javaType = Double.class)
    })
    PlayerProfile selectProfile(@Param("userId") Long userId);

    @Update("update users set city = #{city}, level = #{level}, updated_at = now() where id = #{userId}")
    int updateUserProfileFields(@Param("userId") Long userId, @Param("city") String city, @Param("level") String level);

    @Insert("""
            insert into player_profiles(user_id, sport_code, city, area, longitude, latitude, level, play_style, available_time, intro, allow_invite)
            values (#{userId}, #{sportCode}, #{city}, #{area}, #{longitude}, #{latitude}, #{level}, #{playStyle}, #{availableTime}, #{intro}, #{allowInvite})
            on duplicate key update sport_code = values(sport_code), city = values(city), area = values(area),
              longitude = values(longitude), latitude = values(latitude), level = values(level),
              play_style = values(play_style), available_time = values(available_time), intro = values(intro),
              allow_invite = values(allow_invite), updated_at = now()
            """)
    int upsertProfile(@Param("userId") Long userId,
                      @Param("sportCode") String sportCode,
                      @Param("city") String city,
                      @Param("area") String area,
                      @Param("longitude") Double longitude,
                      @Param("latitude") Double latitude,
                      @Param("level") String level,
                      @Param("playStyle") String playStyle,
                      @Param("availableTime") String availableTime,
                      @Param("intro") String intro,
                      @Param("allowInvite") Boolean allowInvite);

    @Select("""
            <script>
            select count(*)
            from sport_activities a
            where a.status in ('招募中', '已成团')
            <if test="sportCode != null and sportCode != ''">and a.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and a.city = #{city}</if>
            <if test="level != null and level != ''">and a.level_required = #{level}</if>
            </script>
            """)
    Long countActivities(@Param("sportCode") String sportCode, @Param("city") String city, @Param("level") String level);

    @Select("""
            <script>
            select a.id, a.creator_id, u.nickname as creator_name, a.sport_code, a.venue_id,
                   a.place_source, a.place_id, coalesce(a.venue_name, v.name) as venue_name,
                   a.title, a.city, a.start_time, a.end_time, a.max_players, a.current_players,
                   a.level_required, a.fee_type, a.status, a.created_at
            from sport_activities a
            join users u on u.id = a.creator_id
            left join venues v on v.id = a.venue_id
            where a.status in ('招募中', '已成团')
            <if test="sportCode != null and sportCode != ''">and a.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and a.city = #{city}</if>
            <if test="level != null and level != ''">and a.level_required = #{level}</if>
            order by a.start_time asc
            limit #{size} offset #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "creator_id", javaType = Long.class),
            @Arg(column = "creator_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "place_source", javaType = String.class),
            @Arg(column = "place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "start_time", javaType = LocalDateTime.class),
            @Arg(column = "end_time", javaType = LocalDateTime.class),
            @Arg(column = "max_players", javaType = Integer.class),
            @Arg(column = "current_players", javaType = Integer.class),
            @Arg(column = "level_required", javaType = String.class),
            @Arg(column = "fee_type", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<SportActivity> selectActivities(@Param("sportCode") String sportCode,
                                         @Param("city") String city,
                                         @Param("level") String level,
                                         @Param("size") int size,
                                         @Param("offset") int offset);

    @Insert("""
            insert into sport_activities(sport_code, creator_id, venue_id, place_source, place_id, venue_name,
                                         title, city, start_time, end_time, max_players,
                                         current_players, level_required, fee_type, status)
            values (#{row.sportCode}, #{row.creatorId}, #{row.venueId}, #{row.placeSource}, #{row.placeId}, #{row.venueName},
                    #{row.title}, #{row.city}, #{row.startTime}, #{row.endTime}, #{row.maxPlayers},
                    1, #{row.levelRequired}, #{row.feeType}, '招募中')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertActivity(@Param("row") InsertActivityRow row);

    @Insert("insert into sport_activity_members(activity_id, user_id, role, status) values (#{activityId}, #{userId}, #{role}, '已加入')")
    int insertMember(@Param("activityId") Long activityId, @Param("userId") Long userId, @Param("role") String role);

    @Update("""
            update sport_activities
            set current_players = current_players + 1,
                status = case when current_players + 1 >= max_players then '已成团' else '招募中' end
            where id = #{activityId}
            """)
    int increaseActivityPlayers(@Param("activityId") Long activityId);

    @Delete("delete from sport_activity_members where activity_id = #{activityId} and user_id = #{userId}")
    int deleteMember(@Param("activityId") Long activityId, @Param("userId") Long userId);

    @Update("""
            update sport_activities
            set current_players = greatest(current_players - 1, 0),
                status = '招募中'
            where id = #{activityId}
            """)
    int decreaseActivityPlayers(@Param("activityId") Long activityId);

    @Select("""
            select a.id, a.creator_id, u.nickname as creator_name, a.sport_code, a.venue_id,
                   a.place_source, a.place_id, coalesce(a.venue_name, v.name) as venue_name,
                   a.title, a.city, a.start_time, a.end_time, a.max_players, a.current_players,
                   a.level_required, a.fee_type, a.status, a.created_at
            from sport_activities a
            join users u on u.id = a.creator_id
            left join venues v on v.id = a.venue_id
            where a.id = #{id}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "creator_id", javaType = Long.class),
            @Arg(column = "creator_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "place_source", javaType = String.class),
            @Arg(column = "place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "start_time", javaType = LocalDateTime.class),
            @Arg(column = "end_time", javaType = LocalDateTime.class),
            @Arg(column = "max_players", javaType = Integer.class),
            @Arg(column = "current_players", javaType = Integer.class),
            @Arg(column = "level_required", javaType = String.class),
            @Arg(column = "fee_type", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    SportActivity selectActivity(@Param("id") Long id);

    @Select("""
            select m.id, m.activity_id, m.user_id, u.nickname, u.avatar, m.role, m.status, m.created_at
            from sport_activity_members m
            join users u on u.id = m.user_id
            where m.activity_id = #{activityId}
            order by m.created_at
            """)
    List<Map<String, Object>> selectMembers(@Param("activityId") Long activityId);

    @Select("select name from venues where id = #{venueId}")
    String selectVenueName(@Param("venueId") Long venueId);

    class InsertActivityRow {
        private Long id;
        private String sportCode;
        private Long creatorId;
        private Long venueId;
        private String placeSource;
        private String placeId;
        private String venueName;
        private String title;
        private String city;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer maxPlayers;
        private String levelRequired;
        private String feeType;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getSportCode() { return sportCode; }
        public void setSportCode(String sportCode) { this.sportCode = sportCode; }
        public Long getCreatorId() { return creatorId; }
        public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
        public Long getVenueId() { return venueId; }
        public void setVenueId(Long venueId) { this.venueId = venueId; }
        public String getPlaceSource() { return placeSource; }
        public void setPlaceSource(String placeSource) { this.placeSource = placeSource; }
        public String getPlaceId() { return placeId; }
        public void setPlaceId(String placeId) { this.placeId = placeId; }
        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public Integer getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
        public String getLevelRequired() { return levelRequired; }
        public void setLevelRequired(String levelRequired) { this.levelRequired = levelRequired; }
        public String getFeeType() { return feeType; }
        public void setFeeType(String feeType) { this.feeType = feeType; }
    }
}
