package com.hm.badminton.mapper;

import com.hm.badminton.entity.OrderSummary;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EquipmentOrderMapper {

    @Select("""
            select id, user_id, total_amount, status, address, created_at
            from orders
            where user_id = #{userId}
            order by created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "total_amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "address", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<OrderRow> selectByUserId(@Param("userId") Long userId);

    @Select("""
            select product_id, product_name, cover_url, price, quantity
            from order_items
            where order_id = #{orderId}
            order by id
            """)
    @ConstructorArgs({
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class)
    })
    List<OrderSummary.OrderItem> selectItems(@Param("orderId") Long orderId);

    record OrderRow(
            Long id,
            Long userId,
            BigDecimal totalAmount,
            String status,
            String address,
            LocalDateTime createdAt) {
    }
}
