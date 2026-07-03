package com.hm.badminton.mapper.trade;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hm.badminton.entity.VenueOrder;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface VenueOrderMapper extends BaseMapper<VenueOrder> {

    String ORDER_COLUMNS = """
            id, user_id, product_id, inventory_id, venue_id, amap_place_id, venue_name,
            product_title, product_type, service_date, start_time, end_time, amount,
            status, verify_code, paid_at, used_at, created_at
            """;

    @Select("""
            select ${columns}
            from order_venue
            where id = #{orderId} and user_id = #{userId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "inventory_id", javaType = Long.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "amap_place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "product_title", javaType = String.class),
            @Arg(column = "product_type", javaType = String.class),
            @Arg(column = "service_date", javaType = LocalDate.class),
            @Arg(column = "start_time", javaType = LocalTime.class),
            @Arg(column = "end_time", javaType = LocalTime.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "verify_code", javaType = String.class),
            @Arg(column = "paid_at", javaType = LocalDateTime.class),
            @Arg(column = "used_at", javaType = LocalDateTime.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    VenueOrder selectByIdAndUserId(@Param("orderId") Long orderId,
                                   @Param("userId") Long userId,
                                   @Param("columns") String columns);

    @Select("""
            select ${columns}
            from order_venue
            where user_id = #{userId}
            order by created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "inventory_id", javaType = Long.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "amap_place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "product_title", javaType = String.class),
            @Arg(column = "product_type", javaType = String.class),
            @Arg(column = "service_date", javaType = LocalDate.class),
            @Arg(column = "start_time", javaType = LocalTime.class),
            @Arg(column = "end_time", javaType = LocalTime.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "verify_code", javaType = String.class),
            @Arg(column = "paid_at", javaType = LocalDateTime.class),
            @Arg(column = "used_at", javaType = LocalDateTime.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<VenueOrder> selectByUserId(@Param("userId") Long userId, @Param("columns") String columns);
}


