package com.hm.badminton.mapper;

import com.hm.badminton.entity.Venue;
import com.hm.badminton.entity.VenueReview;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface VenueMapper {

    @Select("""
            <script>
            select count(*)
            from venues
            where status = 1
            <if test="city != null and city != ''">and city = #{city}</if>
            <if test="area != null and area != ''">and area = #{area}</if>
            <if test="keyword != null and keyword != ''">
              and (name like concat('%', #{keyword}, '%')
                or address like concat('%', #{keyword}, '%')
                or facilities like concat('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    Long countVenues(@Param("city") String city, @Param("area") String area, @Param("keyword") String keyword);

    @Select("""
            <script>
            select id, name, city, area, address, longitude, latitude, avg_price, score, review_count,
                   open_hours, cover_url, facilities, created_at,
                   <choose>
                     <when test="lng != null and lat != null">
                       (6371000 * acos(least(1, greatest(-1,
                         cos(radians(#{lat})) * cos(radians(latitude)) * cos(radians(longitude) - radians(#{lng})) +
                         sin(radians(#{lat})) * sin(radians(latitude))
                       )))) as distance_m
                     </when>
                     <otherwise>null as distance_m</otherwise>
                   </choose>
            from venues
            where status = 1
            <if test="city != null and city != ''">and city = #{city}</if>
            <if test="area != null and area != ''">and area = #{area}</if>
            <if test="keyword != null and keyword != ''">
              and (name like concat('%', #{keyword}, '%')
                or address like concat('%', #{keyword}, '%')
                or facilities like concat('%', #{keyword}, '%'))
            </if>
            <choose>
              <when test="lng != null and lat != null">order by distance_m asc, score desc</when>
              <otherwise>order by score desc, review_count desc</otherwise>
            </choose>
            limit #{size} offset #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "area", javaType = String.class),
            @Arg(column = "address", javaType = String.class),
            @Arg(column = "longitude", javaType = BigDecimal.class),
            @Arg(column = "latitude", javaType = BigDecimal.class),
            @Arg(column = "avg_price", javaType = Integer.class),
            @Arg(column = "score", javaType = BigDecimal.class),
            @Arg(column = "review_count", javaType = Integer.class),
            @Arg(column = "open_hours", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "facilities", javaType = String.class),
            @Arg(column = "distance_m", javaType = Double.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<Venue> selectVenues(@Param("city") String city,
                             @Param("area") String area,
                             @Param("keyword") String keyword,
                             @Param("lng") Double lng,
                             @Param("lat") Double lat,
                             @Param("size") int size,
                             @Param("offset") int offset);

    @Select("""
            select id, name, city, area, address, longitude, latitude, avg_price, score, review_count,
                   open_hours, cover_url, facilities, created_at, null as distance_m
            from venues
            where id = #{id} and status = 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "area", javaType = String.class),
            @Arg(column = "address", javaType = String.class),
            @Arg(column = "longitude", javaType = BigDecimal.class),
            @Arg(column = "latitude", javaType = BigDecimal.class),
            @Arg(column = "avg_price", javaType = Integer.class),
            @Arg(column = "score", javaType = BigDecimal.class),
            @Arg(column = "review_count", javaType = Integer.class),
            @Arg(column = "open_hours", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "facilities", javaType = String.class),
            @Arg(column = "distance_m", javaType = Double.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    Venue selectVenue(@Param("id") Long id);

    @Select("""
            select id, venue_id, name, court_type, floor_type, price_per_hour, status
            from venue_courts
            where venue_id = #{venueId}
            order by id
            """)
    List<Map<String, Object>> selectCourts(@Param("venueId") Long venueId);

    @Select("""
            select s.id, s.venue_id, s.court_id, c.name as court_name, s.slot_date, s.start_time, s.end_time,
                   s.price, s.status
            from venue_time_slots s
            join venue_courts c on c.id = s.court_id
            where s.venue_id = #{venueId}
            order by s.slot_date, s.start_time
            """)
    List<Map<String, Object>> selectTimeSlots(@Param("venueId") Long venueId);

    @Select("""
            select r.id, r.venue_id, r.user_id, u.nickname, u.avatar, r.rating, r.content,
                   r.image_urls, r.likes, r.created_at
            from venue_reviews r
            join users u on u.id = r.user_id
            where r.venue_id = #{venueId}
            order by r.created_at desc
            limit #{size} offset #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "nickname", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "rating", javaType = Integer.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "image_urls", javaType = String.class),
            @Arg(column = "likes", javaType = Integer.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<VenueReview> selectReviews(@Param("venueId") Long venueId,
                                    @Param("size") int size,
                                    @Param("offset") int offset);

    @Insert("""
            insert into venue_reviews(venue_id, user_id, rating, content, image_urls)
            values (#{venueId}, #{userId}, #{rating}, #{content}, #{imageUrls})
            """)
    int insertReview(@Param("userId") Long userId,
                     @Param("venueId") Long venueId,
                     @Param("rating") Integer rating,
                     @Param("content") String content,
                     @Param("imageUrls") String imageUrls);

    @Update("""
            update venues v
            set score = (select round(avg(rating), 1) from venue_reviews where venue_id = v.id),
                review_count = (select count(*) from venue_reviews where venue_id = v.id)
            where v.id = #{venueId}
            """)
    int refreshVenueRating(@Param("venueId") Long venueId);

    @Select("select count(*) from venue_favorites where user_id = #{userId} and venue_id = #{venueId}")
    Integer countFavorite(@Param("userId") Long userId, @Param("venueId") Long venueId);

    @Delete("delete from venue_favorites where user_id = #{userId} and venue_id = #{venueId}")
    int deleteFavorite(@Param("userId") Long userId, @Param("venueId") Long venueId);

    @Insert("insert into venue_favorites(user_id, venue_id) values (#{userId}, #{venueId})")
    int insertFavorite(@Param("userId") Long userId, @Param("venueId") Long venueId);

    @Select("""
            select v.id, v.name, v.city, v.area, v.address, v.longitude, v.latitude, v.avg_price,
                   v.score, v.review_count, v.open_hours, v.cover_url, v.facilities, v.created_at,
                   null as distance_m
            from venue_favorites f
            join venues v on v.id = f.venue_id
            where f.user_id = #{userId}
            order by f.created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "city", javaType = String.class),
            @Arg(column = "area", javaType = String.class),
            @Arg(column = "address", javaType = String.class),
            @Arg(column = "longitude", javaType = BigDecimal.class),
            @Arg(column = "latitude", javaType = BigDecimal.class),
            @Arg(column = "avg_price", javaType = Integer.class),
            @Arg(column = "score", javaType = BigDecimal.class),
            @Arg(column = "review_count", javaType = Integer.class),
            @Arg(column = "open_hours", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "facilities", javaType = String.class),
            @Arg(column = "distance_m", javaType = Double.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<Venue> selectFavorites(@Param("userId") Long userId);

    @Insert("""
            insert into venues(name, city, area, address, longitude, latitude, avg_price, score,
                               review_count, open_hours, cover_url, facilities, status)
            values (#{row.name}, #{row.city}, #{row.area}, #{row.address}, #{row.longitude}, #{row.latitude},
                    #{row.avgPrice}, 5.0, 0, #{row.openHours}, #{row.coverUrl}, #{row.facilities}, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertVenue(@Param("row") InsertVenueRow row);

    class InsertVenueRow {
        private Long id;
        private String name;
        private String city;
        private String area;
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer avgPrice;
        private String openHours;
        private String coverUrl;
        private String facilities;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public Integer getAvgPrice() { return avgPrice; }
        public void setAvgPrice(Integer avgPrice) { this.avgPrice = avgPrice; }
        public String getOpenHours() { return openHours; }
        public void setOpenHours(String openHours) { this.openHours = openHours; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public String getFacilities() { return facilities; }
        public void setFacilities(String facilities) { this.facilities = facilities; }
    }
}
