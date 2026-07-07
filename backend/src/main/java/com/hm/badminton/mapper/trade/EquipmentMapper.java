package com.hm.badminton.mapper.trade;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.entity.EquipmentCategory;
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

@Mapper
public interface EquipmentMapper {

    @Select("""
            <script>
            select id, sport_code, name, icon, sort
            from equipment_categories
            <where>
              <if test="sportCode != null and sportCode != ''">
                sport_code = #{sportCode}
              </if>
            </where>
            order by sport_code, sort, id
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "icon", javaType = String.class),
            @Arg(column = "sort", javaType = Integer.class)
    })
    List<EquipmentCategory> selectCategories(@Param("sportCode") String sportCode);

    @Select("""
            <script>
            select count(*)
            from equipment p
            where p.status = 1
            <if test="sportCode != null and sportCode != ''">
              and p.sport_code = #{sportCode}
            </if>
            <if test="categoryId != null">
              and p.category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
              and (p.name like concat('%', #{keyword}, '%')
                or p.brand like concat('%', #{keyword}, '%')
                or p.description like concat('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    Long countEquipments(@Param("sportCode") String sportCode,
                       @Param("categoryId") Long categoryId,
                       @Param("keyword") String keyword);

    @Select("""
            <script>
            select p.id, p.sport_code, p.category_id, c.name as category_name, p.name, p.brand, p.description,
                   p.cover_url, p.price, p.stock, p.score, p.sold, p.status, p.created_at
            from equipment p
            join equipment_categories c on c.id = p.category_id
            where p.status = 1
            <if test="sportCode != null and sportCode != ''">
              and p.sport_code = #{sportCode}
            </if>
            <if test="categoryId != null">
              and p.category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
              and (p.name like concat('%', #{keyword}, '%')
                or p.brand like concat('%', #{keyword}, '%')
                or p.description like concat('%', #{keyword}, '%'))
            </if>
            order by p.sold desc, p.score desc, p.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "brand", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "score", javaType = BigDecimal.class),
            @Arg(column = "sold", javaType = Integer.class),
            @Arg(column = "status", javaType = Integer.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<Equipment> selectEquipments(@Param("sportCode") String sportCode,
                                 @Param("categoryId") Long categoryId,
                                 @Param("keyword") String keyword,
                                 @Param("size") int size,
                                 @Param("offset") int offset);

    @Select("""
            select p.id, p.sport_code, p.category_id, c.name as category_name, p.name, p.brand, p.description,
                   p.cover_url, p.price, p.stock, p.score, p.sold, p.status, p.created_at
            from equipment p
            join equipment_categories c on c.id = p.category_id
            where p.id = #{id} and p.status = 1
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "category_id", javaType = Long.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "brand", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "score", javaType = BigDecimal.class),
            @Arg(column = "sold", javaType = Integer.class),
            @Arg(column = "status", javaType = Integer.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    Equipment selectEquipment(@Param("id") Long id);

    @Select("select count(*) from cart_equipment where user_id = #{userId} and product_id = #{productId}")
    Integer countCart(@Param("userId") Long userId, @Param("productId") Long productId);

    @Update("""
            update cart_equipment
            set quantity = least(quantity + #{quantity}, 99), updated_at = now()
            where user_id = #{userId} and product_id = #{productId}
            """)
    int increaseCart(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Insert("insert into cart_equipment(user_id, product_id, quantity) values (#{userId}, #{productId}, #{quantity})")
    int insertCart(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Select("""
            select ci.id, p.id as product_id, p.name, p.brand, p.cover_url, p.price, ci.quantity, p.stock,
                   p.price * ci.quantity as amount
            from cart_equipment ci
            join equipment p on p.id = ci.product_id
            where ci.user_id = #{userId}
            order by ci.updated_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "brand", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class),
            @Arg(column = "stock", javaType = Integer.class),
            @Arg(column = "amount", javaType = BigDecimal.class)
    })
    List<CartItem> selectCart(@Param("userId") Long userId);

    @Delete("delete from cart_equipment where id = #{itemId} and user_id = #{userId}")
    int deleteCart(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Update("""
            update cart_equipment
            set quantity = #{quantity}, updated_at = now()
            where id = #{itemId} and user_id = #{userId}
            """)
    int updateCartQuantity(@Param("userId") Long userId,
                           @Param("itemId") Long itemId,
                           @Param("quantity") Integer quantity);

    @Insert("""
            insert into order_equipment(user_id, total_amount, status, address)
            values (#{row.userId}, #{row.totalAmount}, '待支付', #{row.address})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertOrder(@Param("row") InsertOrderRow row);

    @Update("""
            update equipment
            set stock = stock - #{quantity}, sold = sold + #{quantity}
            where id = #{productId} and stock >= #{quantity}
            """)
    int deductEquipmentStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Insert("""
            insert into order_equipment_item(order_id, product_id, product_name, cover_url, price, quantity)
            values (#{orderId}, #{productId}, #{productName}, #{coverUrl}, #{price}, #{quantity})
            """)
    int insertOrderItem(@Param("orderId") Long orderId,
                        @Param("productId") Long productId,
                        @Param("productName") String productName,
                        @Param("coverUrl") String coverUrl,
                        @Param("price") BigDecimal price,
                        @Param("quantity") Integer quantity);

    @Delete("delete from cart_equipment where user_id = #{userId}")
    int deleteCartByUser(@Param("userId") Long userId);

    @Update("""
            update order_equipment
            set status = '已支付', paid_at = now()
            where id = #{orderId} and user_id = #{userId} and status = '待支付'
            """)
    int payOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @Select("""
            select p.id, p.name, p.cover_url, p.price, ci.quantity
            from cart_equipment ci
            join equipment p on p.id = ci.product_id
            where ci.user_id = #{userId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class)
    })
    List<OrderEquipment> selectOrderEquipmentsFromCart(@Param("userId") Long userId);

    @Insert("""
            insert into equipment(sport_code, category_id, name, brand, description, cover_url, price, stock, score, sold, status)
            values (#{row.sportCode}, #{row.categoryId}, #{row.name}, #{row.brand}, #{row.description}, #{row.coverUrl},
                    #{row.price}, #{row.stock}, 5.0, 0, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertEquipment(@Param("row") InsertEquipmentRow row);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OrderEquipment {
        private Long productId;
        private String name;
        private String coverUrl;
        private BigDecimal price;
        private Integer quantity;
    }

    @Data

    class InsertOrderRow {
        private Long id;
        private Long userId;
        private BigDecimal totalAmount;
        private String address;
    }

    @Data

    class InsertEquipmentRow {
        private Long id;
        private Long categoryId;
        private String sportCode;
        private String name;
        private String brand;
        private String description;
        private String coverUrl;
        private BigDecimal price;
        private Integer stock;
    }
}


