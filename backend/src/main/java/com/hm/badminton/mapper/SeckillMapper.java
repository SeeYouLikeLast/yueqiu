package com.hm.badminton.mapper;

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
            select a.id, a.product_id, p.name as product_name, p.sport_code, p.category_id,
                   c.name as category_name, p.cover_url, p.price as original_price,
                   a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_activities a
            join products p on p.id = a.product_id
            join product_categories c on c.id = p.category_id
            where a.status = 1
            <if test="sportCode != null and sportCode != ''">and p.sport_code = #{sportCode}</if>
            <if test="categoryId != null">and p.category_id = #{categoryId}</if>
            order by a.start_at desc
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
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
    List<SeckillActivity> selectActivities(@Param("sportCode") String sportCode, @Param("categoryId") Long categoryId);

    @Select("""
            select a.id, a.product_id, p.name as product_name, p.sport_code, p.category_id,
                   c.name as category_name, p.cover_url, p.price as original_price,
                   a.seckill_price, a.stock, a.start_at, a.end_at, a.status
            from seckill_activities a
            join products p on p.id = a.product_id
            join product_categories c on c.id = p.category_id
            where a.id = #{activityId} and a.status = 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
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
    SeckillActivity selectActivity(@Param("activityId") Long activityId);

    @Select("""
            select o.id, o.activity_id, o.product_id, p.name as product_name, o.user_id, o.amount,
                   o.status, o.created_at
            from seckill_orders o
            join products p on p.id = o.product_id
            where o.id = #{orderId} and o.user_id = #{userId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "activity_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    SeckillOrder selectOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @Select("""
            select o.id, o.activity_id, o.product_id, p.name as product_name, o.user_id, o.amount,
                   o.status, o.created_at
            from seckill_orders o
            join products p on p.id = o.product_id
            where o.user_id = #{userId}
            order by o.created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "activity_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<SeckillOrder> selectOrdersByUser(@Param("userId") Long userId);

    @Select("select count(*) from seckill_orders where user_id = #{userId} and activity_id = #{activityId}")
    Integer countUserActivityOrder(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Select("select user_id from seckill_orders where activity_id = #{activityId}")
    List<Long> selectUserIdsByActivity(@Param("activityId") Long activityId);

    @Update("update seckill_activities set stock = stock - 1 where id = #{activityId} and stock > 0")
    int deductActivityStock(@Param("activityId") Long activityId);

    @Insert("""
            insert into seckill_orders(id, activity_id, product_id, user_id, amount, status)
            values (#{orderId}, #{activityId}, #{productId}, #{userId}, #{amount}, '已抢到')
            """)
    int insertOrder(@Param("orderId") Long orderId,
                    @Param("activityId") Long activityId,
                    @Param("productId") Long productId,
                    @Param("userId") Long userId,
                    @Param("amount") BigDecimal amount);

    @Update("update seckill_activities set stock = greatest(stock - 1, 0) where id = #{activityId}")
    int deductActivityStockLenient(@Param("activityId") Long activityId);

    @Update("update products set stock = greatest(stock - 1, 0), sold = sold + 1 where id = #{productId}")
    int deductProductStockLenient(@Param("productId") Long productId);
}
