package com.hm.badminton.mapper.place;

import com.hm.badminton.entity.VenueReview;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VenueMapper {

    @Select("""
            select id
            from place
            where status = 1
              and replace(city, '市', '') = replace(#{city}, '市', '')
              and sport_code = #{sportCode}
            order by id
            limit 1 offset #{offset}
            """)
    Long selectPlaceIdBySlot(@Param("city") String city,
                             @Param("sportCode") String sportCode,
                             @Param("offset") int offset);

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
}
