package com.hm.badminton.mapper;

import com.hm.badminton.entity.VenueProductInventory;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface VenueProductMapper {

    @Select("""
            <script>
            select count(*)
            from venue_products p
            where p.status = 1
            <if test="sportCode != null and sportCode != ''">and p.sport_code = #{sportCode}</if>
            <if test="productType != null and productType != ''">and p.product_type = #{productType}</if>
            <if test="keyword != null and keyword != ''">
              and (p.title like concat('%', #{keyword}, '%')
                or p.venue_name like concat('%', #{keyword}, '%')
                or p.description like concat('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    Long countSaleProducts(@Param("sportCode") String sportCode,
                           @Param("productType") String productType,
                           @Param("keyword") String keyword);

    @Select("""
            <script>
            select p.id, p.venue_id, p.amap_place_id, p.venue_name, p.place_rank, p.sport_code, p.product_type,
                   p.title, p.description, p.cover_url, p.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, p.sale_start_at, p.sale_end_at,
                   coalesce(sum(case when i.status = '可售' and i.service_date >= current_date then i.available_stock else 0 end), 0) as available_stock
            from venue_products p
            left join venue_product_inventory i on i.product_id = p.id
            where p.status = 1
            <if test="sportCode != null and sportCode != ''">and p.sport_code = #{sportCode}</if>
            <if test="amapPlaceId != null and amapPlaceId != ''">and p.amap_place_id = #{amapPlaceId}</if>
            <if test="venueId != null">and p.venue_id = #{venueId}</if>
            <if test="placeRank != null">and p.place_rank = #{placeRank}</if>
            <if test="productType != null and productType != ''">and p.product_type = #{productType}</if>
            <if test="keyword != null and keyword != ''">
              and (p.title like concat('%', #{keyword}, '%')
                or p.venue_name like concat('%', #{keyword}, '%')
                or p.description like concat('%', #{keyword}, '%'))
            </if>
            group by p.id, p.venue_id, p.amap_place_id, p.venue_name, p.place_rank, p.sport_code, p.product_type,
                     p.title, p.description, p.cover_url, p.price, p.original_price, p.tags,
                     p.use_rule, p.refund_rule, p.sale_start_at, p.sale_end_at
            order by field(p.product_type, 'TIME_PACKAGE', 'COURT_SLOT', 'COACH_LESSON'), p.price, p.id
            <if test="offset != null">limit #{limit} offset #{offset}</if>
            <if test="offset == null">limit #{limit}</if>
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "amap_place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "place_rank", javaType = Integer.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "product_type", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "tags", javaType = String.class),
            @Arg(column = "use_rule", javaType = String.class),
            @Arg(column = "refund_rule", javaType = String.class),
            @Arg(column = "available_stock", javaType = Integer.class),
            @Arg(column = "sale_start_at", javaType = LocalDateTime.class),
            @Arg(column = "sale_end_at", javaType = LocalDateTime.class)
    })
    List<VenueProductRow> selectProducts(@Param("sportCode") String sportCode,
                                         @Param("amapPlaceId") String amapPlaceId,
                                         @Param("venueId") Long venueId,
                                         @Param("placeRank") Integer placeRank,
                                         @Param("productType") String productType,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit,
                                         @Param("offset") Integer offset);

    @Select("""
            select p.id, p.venue_id, p.amap_place_id, p.venue_name, p.place_rank, p.sport_code, p.product_type,
                   p.title, p.description, p.cover_url, p.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, p.sale_start_at, p.sale_end_at,
                   coalesce(sum(case when i.status = '可售' and i.service_date >= current_date then i.available_stock else 0 end), 0) as available_stock
            from venue_products p
            left join venue_product_inventory i on i.product_id = p.id
            where p.id = #{productId} and p.status = 1
            group by p.id, p.venue_id, p.amap_place_id, p.venue_name, p.place_rank, p.sport_code, p.product_type,
                     p.title, p.description, p.cover_url, p.price, p.original_price, p.tags,
                     p.use_rule, p.refund_rule, p.sale_start_at, p.sale_end_at
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "venue_id", javaType = Long.class),
            @Arg(column = "amap_place_id", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "place_rank", javaType = Integer.class),
            @Arg(column = "sport_code", javaType = String.class),
            @Arg(column = "product_type", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "description", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "original_price", javaType = BigDecimal.class),
            @Arg(column = "tags", javaType = String.class),
            @Arg(column = "use_rule", javaType = String.class),
            @Arg(column = "refund_rule", javaType = String.class),
            @Arg(column = "available_stock", javaType = Integer.class),
            @Arg(column = "sale_start_at", javaType = LocalDateTime.class),
            @Arg(column = "sale_end_at", javaType = LocalDateTime.class)
    })
    VenueProductRow selectProduct(@Param("productId") Long productId);

    @Select("""
            <script>
            select i.id, i.product_id, i.court_name, i.coach_id, c.name as coach_name,
                   i.service_date, i.start_time, i.end_time, i.total_stock, i.available_stock,
                   i.sold_stock, i.price, i.status
            from venue_product_inventory i
            left join coaches c on c.id = i.coach_id
            where i.product_id = #{productId} and i.status = '可售'
            <if test="date != null">and i.service_date = #{date}</if>
            <if test="date == null">and i.service_date >= current_date</if>
            order by i.service_date, i.start_time, i.id
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "court_name", javaType = String.class),
            @Arg(column = "coach_id", javaType = Long.class),
            @Arg(column = "coach_name", javaType = String.class),
            @Arg(column = "service_date", javaType = LocalDate.class),
            @Arg(column = "start_time", javaType = LocalTime.class),
            @Arg(column = "end_time", javaType = LocalTime.class),
            @Arg(column = "total_stock", javaType = Integer.class),
            @Arg(column = "available_stock", javaType = Integer.class),
            @Arg(column = "sold_stock", javaType = Integer.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class)
    })
    List<VenueProductInventory> selectInventories(@Param("productId") Long productId, @Param("date") LocalDate date);

    @Select("""
            select p.id as product_id, i.id as inventory_id, p.venue_id, p.amap_place_id,
                   p.venue_name, p.title as product_title, p.product_type, i.service_date,
                   i.start_time, i.end_time, i.available_stock, i.price
            from venue_products p
            join venue_product_inventory i on i.product_id = p.id
            where p.id = #{productId} and i.id = #{inventoryId} and p.status = 1 and i.status = '可售'
            """)
    @ConstructorArgs({
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
            @Arg(column = "available_stock", javaType = Integer.class),
            @Arg(column = "price", javaType = BigDecimal.class)
    })
    ProductSale selectSale(@Param("productId") Long productId, @Param("inventoryId") Long inventoryId);

    @Update("""
            update venue_product_inventory
            set available_stock = available_stock - 1,
                sold_stock = sold_stock + 1,
                updated_at = now()
            where id = #{inventoryId} and product_id = #{productId} and status = '可售' and available_stock > 0
            """)
    int deductInventory(@Param("productId") Long productId, @Param("inventoryId") Long inventoryId);

    @Insert("""
            insert into venue_orders(user_id, product_id, inventory_id, venue_id, amap_place_id,
                                     venue_name, product_title, product_type, service_date,
                                     start_time, end_time, amount, status, verify_code)
            values (#{row.userId}, #{row.productId}, #{row.inventoryId}, #{row.venueId}, #{row.amapPlaceId},
                    #{row.venueName}, #{row.productTitle}, #{row.productType}, #{row.serviceDate},
                    #{row.startTime}, #{row.endTime}, #{row.amount}, '待支付', #{row.verifyCode})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertVenueOrder(@Param("row") InsertVenueOrderRow row);

    @Select("""
            select id
            from venue_product_inventory
            where product_id = #{productId} and status = '可售' and available_stock > 0 and service_date >= current_date
            order by service_date, start_time, id
            limit 1
            """)
    Long selectFirstAvailableInventoryId(@Param("productId") Long productId);

    @Update("""
            update venue_orders
            set venue_id = #{venueId},
                amap_place_id = #{amapPlaceId},
                venue_name = #{venueName},
                updated_at = now()
            where id = #{orderId} and user_id = #{userId}
            """)
    int updateOrderVenueContext(@Param("userId") Long userId,
                                @Param("orderId") Long orderId,
                                @Param("venueId") Long venueId,
                                @Param("amapPlaceId") String amapPlaceId,
                                @Param("venueName") String venueName);

    @Update("""
            update venue_orders
            set status = '已支付', paid_at = now(), updated_at = now()
            where id = #{orderId} and user_id = #{userId} and status = '待支付'
            """)
    int payVenueOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    record VenueProductRow(
            Long id,
            Long venueId,
            String amapPlaceId,
            String venueName,
            Integer placeRank,
            String sportCode,
            String productType,
            String title,
            String description,
            String coverUrl,
            BigDecimal price,
            BigDecimal originalPrice,
            String tags,
            String useRule,
            String refundRule,
            Integer availableStock,
            LocalDateTime saleStartAt,
            LocalDateTime saleEndAt) {
    }

    record ProductSale(
            Long productId,
            Long inventoryId,
            Long venueId,
            String amapPlaceId,
            String venueName,
            String productTitle,
            String productType,
            LocalDate serviceDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer availableStock,
            BigDecimal price) {
    }

    class InsertVenueOrderRow {
        private Long id;
        private Long userId;
        private Long productId;
        private Long inventoryId;
        private Long venueId;
        private String amapPlaceId;
        private String venueName;
        private String productTitle;
        private String productType;
        private LocalDate serviceDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private BigDecimal amount;
        private String verifyCode;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Long getInventoryId() { return inventoryId; }
        public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
        public Long getVenueId() { return venueId; }
        public void setVenueId(Long venueId) { this.venueId = venueId; }
        public String getAmapPlaceId() { return amapPlaceId; }
        public void setAmapPlaceId(String amapPlaceId) { this.amapPlaceId = amapPlaceId; }
        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public LocalDate getServiceDate() { return serviceDate; }
        public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
        public LocalTime getEndTime() { return endTime; }
        public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getVerifyCode() { return verifyCode; }
        public void setVerifyCode(String verifyCode) { this.verifyCode = verifyCode; }
    }
}
