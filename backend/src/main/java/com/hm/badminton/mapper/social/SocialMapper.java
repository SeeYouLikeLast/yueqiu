package com.hm.badminton.mapper.social;

import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import lombok.Data;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
            <if test="excludeUserId != null">and pp.user_id != #{excludeUserId}</if>
            </script>
            """)
    Long countPlayers(@Param("sportCode") String sportCode,
                      @Param("city") String city,
                      @Param("area") String area,
                      @Param("level") String level,
                      @Param("excludeUserId") Long excludeUserId);

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
            <if test="excludeUserId != null">and pp.user_id != #{excludeUserId}</if>
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
                                      @Param("offset") int offset,
                                      @Param("excludeUserId") Long excludeUserId);

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
            where a.status in ('招募中', '已满员')
              and a.end_time &gt; #{currentTime}
            <if test="sportCode != null and sportCode != ''">and a.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and a.city = #{city}</if>
            <if test="level != null and level != ''">and a.level_required = #{level}</if>
            <if test="scope == 'created'">and a.creator_id = #{userId}</if>
            <if test="scope == 'joined'">
              and a.creator_id &lt;&gt; #{userId}
              and exists (select 1 from sport_activity_members m where m.activity_id = a.id and m.user_id = #{userId})
            </if>
            <if test="scope == 'others' and userId != null">
              and a.creator_id &lt;&gt; #{userId}
              and not exists (select 1 from sport_activity_members m where m.activity_id = a.id and m.user_id = #{userId})
            </if>
            </script>
            """)
    Long countActivities(@Param("sportCode") String sportCode,
                         @Param("city") String city,
                         @Param("level") String level,
                         @Param("userId") Long userId,
                         @Param("scope") String scope,
                         @Param("currentTime") LocalDateTime currentTime);

    @Select("""
            <script>
            select a.id, a.creator_id, u.nickname as creator_name, a.sport_code, a.venue_id,
                   a.place_source, a.place_id, coalesce(a.venue_name, v.name) as venue_name,
                   a.title, a.city, a.start_time, a.end_time, a.max_players, a.current_players,
                   a.level_required, a.fee_type, a.status, a.created_at
            from sport_activities a
            join users u on u.id = a.creator_id
            left join place v on v.id = a.venue_id
            where a.status in ('招募中', '已满员')
              and a.end_time &gt; #{currentTime}
            <if test="sportCode != null and sportCode != ''">and a.sport_code = #{sportCode}</if>
            <if test="city != null and city != ''">and a.city = #{city}</if>
            <if test="level != null and level != ''">and a.level_required = #{level}</if>
            <if test="scope == 'created'">and a.creator_id = #{userId}</if>
            <if test="scope == 'joined'">
              and a.creator_id &lt;&gt; #{userId}
              and exists (select 1 from sport_activity_members m where m.activity_id = a.id and m.user_id = #{userId})
            </if>
            <if test="scope == 'others' and userId != null">
              and a.creator_id &lt;&gt; #{userId}
              and not exists (select 1 from sport_activity_members m where m.activity_id = a.id and m.user_id = #{userId})
            </if>
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
                                         @Param("userId") Long userId,
                                         @Param("scope") String scope,
                                         @Param("currentTime") LocalDateTime currentTime,
                                         @Param("size") int size,
                                         @Param("offset") int offset);

    /**
     * 演示活动按周循环。任务即使停机多天，也会一次顺延足够的整周，
     * 保证结束时间重新落到 currentTime 之后，而不是每天无限向后推尚未发生的活动。
     */
    @Update("""
            update sport_activities
            set start_time = timestampadd(
                    day,
                    7 * (floor(greatest(timestampdiff(day, end_time, #{currentTime}), 0) / 7) + 1),
                    start_time),
                end_time = timestampadd(
                    day,
                    7 * (floor(greatest(timestampdiff(day, end_time, #{currentTime}), 0) / 7) + 1),
                    end_time),
                status = case when current_players >= max_players then '已满员' else '招募中' end
            where left(place_id, 5) = 'DEMO_'
              and end_time <= #{currentTime}
            """)
    int refreshExpiredDemoActivities(@Param("currentTime") LocalDateTime currentTime);

    @Update("""
            update sport_activities
            set status = '已结束'
            where status in ('招募中', '已满员')
              and end_time <= #{currentTime}
              and (place_id is null or left(place_id, 5) <> 'DEMO_')
            """)
    int finishExpiredUserActivities(@Param("currentTime") LocalDateTime currentTime);

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
                status = case when current_players + 1 >= max_players then '已满员' else '招募中' end
            where id = #{activityId}
              and status = '招募中'
              and current_players < max_players
            """)
    int increaseActivityPlayersIfAvailable(@Param("activityId") Long activityId);

    @Select("""
            select a.id, a.creator_id, u.nickname as creator_name, a.sport_code, a.venue_id,
                   a.place_source, a.place_id, coalesce(a.venue_name, v.name) as venue_name,
                   a.title, a.city, a.start_time, a.end_time, a.max_players, a.current_players,
                   a.level_required, a.fee_type, a.status, a.created_at
            from sport_activities a
            join users u on u.id = a.creator_id
            left join place v on v.id = a.venue_id
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

    @Select("select name from place where id = #{venueId}")
    String selectVenueName(@Param("venueId") Long venueId);

    @Data
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
    }
}
