package com.hm.badminton.mapper;

import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.Product;
import com.hm.badminton.entity.ProductCategory;
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
            from product_categories
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
    List<ProductCategory> selectCategories(@Param("sportCode") String sportCode);

    @Select("""
            <script>
            select count(*)
            from products p
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
    Long countProducts(@Param("sportCode") String sportCode,
                       @Param("categoryId") Long categoryId,
                       @Param("keyword") String keyword);

    @Select("""
            <script>
            select p.id, p.sport_code, p.category_id, c.name as category_name, p.name, p.brand, p.description,
                   p.cover_url, p.price, p.stock, p.score, p.sold, p.status, p.created_at
            from products p
            join product_categories c on c.id = p.category_id
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
    List<Product> selectProducts(@Param("sportCode") String sportCode,
                                 @Param("categoryId") Long categoryId,
                                 @Param("keyword") String keyword,
                                 @Param("size") int size,
                                 @Param("offset") int offset);

    @Select("""
            select p.id, p.sport_code, p.category_id, c.name as category_name, p.name, p.brand, p.description,
                   p.cover_url, p.price, p.stock, p.score, p.sold, p.status, p.created_at
            from products p
            join product_categories c on c.id = p.category_id
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
    Product selectProduct(@Param("id") Long id);

    @Select("select count(*) from cart_items where user_id = #{userId} and product_id = #{productId}")
    Integer countCart(@Param("userId") Long userId, @Param("productId") Long productId);

    @Update("""
            update cart_items
            set quantity = least(quantity + #{quantity}, 99), updated_at = now()
            where user_id = #{userId} and product_id = #{productId}
            """)
    int increaseCart(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Insert("insert into cart_items(user_id, product_id, quantity) values (#{userId}, #{productId}, #{quantity})")
    int insertCart(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Select("""
            select ci.id, p.id as product_id, p.name, p.brand, p.cover_url, p.price, ci.quantity, p.stock,
                   p.price * ci.quantity as amount
            from cart_items ci
            join products p on p.id = ci.product_id
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

    @Delete("delete from cart_items where id = #{itemId} and user_id = #{userId}")
    int deleteCart(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Insert("""
            insert into orders(user_id, total_amount, status, address)
            values (#{row.userId}, #{row.totalAmount}, '待支付', #{row.address})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertOrder(@Param("row") InsertOrderRow row);

    @Update("""
            update products
            set stock = stock - #{quantity}, sold = sold + #{quantity}
            where id = #{productId} and stock >= #{quantity}
            """)
    int deductProductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Insert("""
            insert into order_items(order_id, product_id, product_name, cover_url, price, quantity)
            values (#{orderId}, #{productId}, #{productName}, #{coverUrl}, #{price}, #{quantity})
            """)
    int insertOrderItem(@Param("orderId") Long orderId,
                        @Param("productId") Long productId,
                        @Param("productName") String productName,
                        @Param("coverUrl") String coverUrl,
                        @Param("price") BigDecimal price,
                        @Param("quantity") Integer quantity);

    @Delete("delete from cart_items where user_id = #{userId}")
    int deleteCartByUser(@Param("userId") Long userId);

    @Update("""
            update orders
            set status = '已支付', paid_at = now()
            where id = #{orderId} and user_id = #{userId} and status = '待支付'
            """)
    int payOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @Select("""
            select p.id, p.name, p.cover_url, p.price, ci.quantity
            from cart_items ci
            join products p on p.id = ci.product_id
            where ci.user_id = #{userId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class)
    })
    List<OrderProduct> selectOrderProductsFromCart(@Param("userId") Long userId);

    @Insert("""
            insert into products(sport_code, category_id, name, brand, description, cover_url, price, stock, score, sold, status)
            values (#{row.sportCode}, #{row.categoryId}, #{row.name}, #{row.brand}, #{row.description}, #{row.coverUrl},
                    #{row.price}, #{row.stock}, 5.0, 0, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertProduct(@Param("row") InsertProductRow row);

    record OrderProduct(Long productId, String name, String coverUrl, BigDecimal price, Integer quantity) {
    }

    class InsertOrderRow {
        private Long id;
        private Long userId;
        private BigDecimal totalAmount;
        private String address;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    class InsertProductRow {
        private Long id;
        private Long categoryId;
        private String sportCode;
        private String name;
        private String brand;
        private String description;
        private String coverUrl;
        private BigDecimal price;
        private Integer stock;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getSportCode() { return sportCode; }
        public void setSportCode(String sportCode) { this.sportCode = sportCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }
}
