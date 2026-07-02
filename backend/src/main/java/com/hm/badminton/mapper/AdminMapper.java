package com.hm.badminton.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {

    @Select("select count(*) from ${table}")
    Long countTable(@Param("table") String table);

    @Select("select coalesce(sum(total_amount), 0) from order_equipment where status = '已支付'")
    BigDecimal paidRevenue();

    @Select("""
            select id, name, city, area, score, review_count
            from place
            order by review_count desc, score desc
            limit 8
            """)
    List<Map<String, Object>> hotVenues();

    @Select("""
            select p.id, p.name, c.name as category_name, p.brand, p.price, p.stock, p.sold, p.score
            from equipment p
            join equipment_categories c on c.id = p.category_id
            order by p.sold desc, p.score desc
            limit 8
            """)
    List<Map<String, Object>> hotEquipments();

    @Select("""
            select r.id, v.name as venue_name, u.nickname, r.rating, r.content, r.created_at
            from venue_reviews r
            join place v on v.id = r.venue_id
            join users u on u.id = r.user_id
            order by r.created_at desc
            limit 10
            """)
    List<Map<String, Object>> latestReviews();
}
