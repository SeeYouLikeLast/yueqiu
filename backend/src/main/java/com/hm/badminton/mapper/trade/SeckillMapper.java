package com.hm.badminton.mapper.trade;

import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SeckillMapper {

    @Select("""
            <script>
            select a.id, 2 as type, a.equipment_id as product_id, p.name as product_name, p.sport_code, p.category_id,
                   c.name as category_name, p.cover_url, p.price as original_price,
                   a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_equipment a
            join equipment p on p.id = a.equipment_id
            join equipment_categories c on c.id = p.category_id
            where a.status = 1
            <if test="sportCode != null and sportCode != ''">and p.sport_code = #{sportCode}</if>
            <if test="categoryId != null">and p.category_id = #{categoryId}</if>
            order by a.start_at desc
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "seckill_price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "start_at", javaType = LocalDateTime.class),
            @Arg(column = "end_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = Integer.class)
    })
    List<SeckillActivity> selectEquipmentActivities(@Param("sportCode") String sportCode,
                                                    @Param("categoryId") Long categoryId);

    @Select("""
            <script>
            select a.id, 1 as type, a.venue_id as product_id, v.title as product_name, v.sport_code,
                   null as category_id, v.product_type as category_name, v.cover_url,
                   v.original_price as original_price, a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_venue a
            join venue v on v.id = a.venue_id
            where a.status = 1 and v.status = 1
            <if test="sportCode != null and sportCode != ''">and v.sport_code = #{sportCode}</if>
            order by a.start_at desc
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "seckill_price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "start_at", javaType = LocalDateTime.class),
            @Arg(column = "end_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = Integer.class)
    })
    List<SeckillActivity> selectVenueActivities(@Param("sportCode") String sportCode);

    @Select("""
            select a.id, 2 as type, a.equipment_id as product_id, p.name as product_name, p.sport_code, p.category_id,
                   c.name as category_name, p.cover_url, p.price as original_price,
                   a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_equipment a
            join equipment p on p.id = a.equipment_id
            join equipment_categories c on c.id = p.category_id
            where a.id = #{activityId} and a.status = 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "seckill_price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "start_at", javaType = LocalDateTime.class),
            @Arg(column = "end_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = Integer.class)
    })
    SeckillActivity selectEquipmentActivity(@Param("activityId") Long activityId);

    @Select("""
            select a.id, 1 as type, a.venue_id as product_id, v.title as product_name, v.sport_code,
                   null as category_id, v.product_type as category_name, v.cover_url,
                   v.original_price as original_price, a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_venue a
            join venue v on v.id = a.venue_id
            where a.id = #{activityId} and a.status = 1 and v.status = 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "seckill_price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "start_at", javaType = LocalDateTime.class),
            @Arg(column = "end_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = Integer.class)
    })
    SeckillActivity selectVenueActivity(@Param("activityId") Long activityId);

    @Select("""
            select o.id, 2 as type, o.seckill_id as activity_id, o.equipment_id as product_id,
                   p.name as product_name, o.user_id, o.amount, o.status, o.created_at
            from order_seckill_equipment o
            join equipment p on p.id = o.equipment_id
            where o.user_id = #{userId}
            order by o.created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "activity_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<SeckillOrder> selectEquipmentOrdersByUser(@Param("userId") Long userId);

    @Select("""
            select o.id, 1 as type, o.seckill_id as activity_id, o.venue_id as product_id,
                   v.title as product_name, o.user_id, o.amount, o.status, o.created_at
            from order_seckill_venue o
            join venue v on v.id = o.venue_id
            where o.user_id = #{userId}
            order by o.created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "type", javaType = Integer.class),
            @Arg(column = "activity_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<SeckillOrder> selectVenueOrdersByUser(@Param("userId") Long userId);

    @Select("select count(*) from order_seckill_equipment where user_id = #{userId} and seckill_id = #{activityId}")
    Integer countUserEquipmentOrder(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Select("select count(*) from order_seckill_venue where user_id = #{userId} and seckill_id = #{activityId}")
    Integer countUserVenueOrder(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Select("select user_id from order_seckill_equipment where seckill_id = #{activityId}")
    List<Long> selectEquipmentUserIdsByActivity(@Param("activityId") Long activityId);

    @Select("select user_id from order_seckill_venue where seckill_id = #{activityId}")
    List<Long> selectVenueUserIdsByActivity(@Param("activityId") Long activityId);

    @Update("update seckill_equipment set stock = stock - 1 where id = #{activityId} and stock > 0")
    int deductEquipmentActivityStock(@Param("activityId") Long activityId);

    @Update("update seckill_venue set stock = stock - 1 where id = #{activityId} and stock > 0")
    int deductVenueActivityStock(@Param("activityId") Long activityId);

    @Insert("""
            insert into order_seckill_equipment(id, seckill_id, equipment_id, user_id, amount, status)
            values (#{orderId}, #{activityId}, #{productId}, #{userId}, #{amount}, '已抢到')
            """)
    int insertEquipmentOrder(@Param("orderId") Long orderId,
                             @Param("activityId") Long activityId,
                             @Param("productId") Long productId,
                             @Param("userId") Long userId,
                             @Param("amount") BigDecimal amount);

    @Insert("""
            insert into order_seckill_venue(id, seckill_id, venue_id, user_id, amount, status)
            values (#{orderId}, #{activityId}, #{productId}, #{userId}, #{amount}, '已抢到')
            """)
    int insertVenueOrder(@Param("orderId") Long orderId,
                         @Param("activityId") Long activityId,
                         @Param("productId") Long productId,
                         @Param("userId") Long userId,
                         @Param("amount") BigDecimal amount);

    @Update("update equipment set stock = greatest(stock - 1, 0), sold = sold + 1 where id = #{productId}")
    int deductEquipmentStockLenient(@Param("productId") Long productId);
}
