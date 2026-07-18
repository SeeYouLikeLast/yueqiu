package com.hm.badminton.mapper.trade;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.hm.badminton.entity.VenueInventory;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.vo.AgentVenueProductVO;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface VenueItemMapper {

    @Select("""
            <script>
            select count(*)
            from venue p
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
    Long countSaleItems(@Param("sportCode") String sportCode,
                           @Param("productType") String productType,
                           @Param("keyword") String keyword);

    @Select("""
            <script>
            select p.id, p.venue_id, p.amap_place_id, p.venue_name, p.place_rank, p.sport_code, p.product_type,
                   p.title, p.description, p.cover_url, p.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, p.sale_start_at, p.sale_end_at,
                   coalesce(sum(case when i.status = '可售' and i.service_date >= current_date then i.available_stock else 0 end), 0) as available_stock
            from venue p
            left join venue_inventory i on i.product_id = p.id
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
    List<VenueItemRow> selectItems(@Param("sportCode") String sportCode,
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
            from venue p
            left join venue_inventory i on i.product_id = p.id
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
    VenueItemRow selectItem(@Param("productId") Long productId);

    @Select("""
            <script>
            select i.id, i.product_id, i.court_name, i.coach_id, c.name as coach_name,
                   i.service_date, i.start_time, i.end_time, i.total_stock, i.available_stock,
                   i.sold_stock, i.price, i.status
            from venue_inventory i
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
    List<VenueInventory> selectInventories(@Param("productId") Long productId, @Param("date") LocalDate date);

    /**
     * 找出已经过期的排序绑定演示库存。真实场馆库存会绑定 venue_id，不参与自动滚动。
     * max_service_date 用于把过期行依次接到该商品现有最晚日期之后。
     */
    @Select("""
            select demo.id,
                   demo.product_id as productId,
                   demo.service_date as serviceDate,
                   demo.max_service_date as maxServiceDate
            from (
                select i.id, i.product_id, i.service_date,
                       max(i.service_date) over (partition by i.product_id) as max_service_date
                from venue_inventory i
                join venue p on p.id = i.product_id
                where i.venue_id is null
                  and p.venue_id is null
                  and p.place_rank is not null
            ) demo
            where demo.service_date < #{currentDate}
            order by demo.product_id, demo.service_date, demo.id
            """)
    List<ExpiredDemoInventoryRow> selectExpiredDemoInventories(@Param("currentDate") LocalDate currentDate);

    /** 查询排序绑定演示商品当前覆盖的日期窗口。 */
    @Select("""
            select i.product_id as productId,
                   min(i.service_date) as minServiceDate,
                   max(i.service_date) as maxServiceDate
            from venue_inventory i
            join venue p on p.id = i.product_id
            where i.venue_id is null
              and p.venue_id is null
              and p.place_rank is not null
            group by i.product_id
            """)
    List<DemoInventoryWindowRow> selectDemoInventoryWindows();

    /** 当整个演示窗口从明天开始时，将它整体平移到今天，保证“今晚”存在真实可选场次。 */
    @Update("""
            update venue_inventory i
            join venue p on p.id = i.product_id
            set i.service_date = date_sub(i.service_date, interval #{days} day)
            where i.product_id = #{productId}
              and i.venue_id is null
              and p.venue_id is null
              and p.place_rank is not null
            """)
    int shiftDemoInventoryWindow(@Param("productId") Long productId, @Param("days") long days);

    /**
     * Demo single-court inventory starts with a small capacity. Raise it once to a stable showcase
     * capacity while preserving already sold and locked quantities. Real venue-bound inventory is untouched.
     */
    @Update("""
            update venue_inventory i
            join venue p on p.id = i.product_id
            set i.available_stock = i.available_stock + (8 - i.total_stock),
                i.total_stock = 8
            where i.venue_id is null
              and p.venue_id is null
              and p.place_rank is not null
              and p.product_type = 'COURT_SLOT'
              and i.total_stock < 8
            """)
    int ensureDemoCourtCapacity();

    /** 将一条已过期演示库存复用为新的未来场次，并重置这个新场次的库存。 */
    @Update("""
            update venue_inventory
            set service_date = #{nextServiceDate},
                available_stock = greatest(total_stock - mod(id, 3), 1),
                locked_stock = 0,
                sold_stock = total_stock - greatest(total_stock - mod(id, 3), 1),
                status = '可售'
            where id = #{id} and service_date < #{currentDate}
            """)
    int rollDemoInventory(@Param("id") Long id,
                          @Param("nextServiceDate") LocalDate nextServiceDate,
                          @Param("currentDate") LocalDate currentDate);

    @Select("""
            <script>
            select p.id as product_id, i.id as inventory_id, p.venue_id, p.amap_place_id,
                   p.venue_name, p.place_rank, p.sport_code, p.product_type, p.title,
                   p.description, p.cover_url, i.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, i.service_date, i.start_time, i.end_time,
                   i.available_stock
            from venue p
            join venue_inventory i on i.product_id = p.id
            where p.status = 1 and i.status = '可售' and i.available_stock &gt; 0
              and p.sport_code = #{sportCode} and p.place_rank = #{placeRank}
              and i.service_date = #{targetDate} and i.start_time = #{startTime}
            <if test="endTime != null">and i.end_time = #{endTime}</if>
            <if test="maxBudget != null">and i.price &lt;= #{maxBudget}</if>
            order by i.price, p.id, i.id
            limit #{limit}
            </script>
            """)
    List<AgentVenueProductVO> selectExactAgentCandidates(@Param("sportCode") String sportCode,
                                                         @Param("placeRank") Integer placeRank,
                                                         @Param("targetDate") LocalDate targetDate,
                                                         @Param("startTime") LocalTime startTime,
                                                         @Param("endTime") LocalTime endTime,
                                                         @Param("maxBudget") BigDecimal maxBudget,
                                                         @Param("limit") int limit);

    @Data
    class ExpiredDemoInventoryRow {
        private Long id;
        private Long productId;
        private LocalDate serviceDate;
        private LocalDate maxServiceDate;
    }

    @Data
    class DemoInventoryWindowRow {
        private Long productId;
        private LocalDate minServiceDate;
        private LocalDate maxServiceDate;
    }

    @Select("""
            <script>
            select p.id as product_id, i.id as inventory_id, p.venue_id, p.amap_place_id,
                   p.venue_name, p.place_rank, p.sport_code, p.product_type, p.title,
                   p.description, p.cover_url, i.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, i.service_date, i.start_time, i.end_time,
                   i.available_stock
            from venue p
            join venue_inventory i on i.product_id = p.id
            where p.status = 1 and i.status = '可售' and i.available_stock &gt; 0
              and p.sport_code = #{sportCode} and p.place_rank = #{placeRank}
              and p.product_type = 'COURT_SLOT'
              and i.service_date = #{targetDate}
              and timestampdiff(minute, i.start_time, i.end_time) = 60
              and i.start_time &gt;= #{startTime}
            <if test="windowEnd != null">and i.start_time &lt; #{windowEnd}</if>
            <if test="maxBudget != null">and i.price &lt;= #{maxBudget}</if>
            order by abs(time_to_sec(timediff(i.start_time, #{startTime}))), i.price, p.id, i.id
            limit #{limit}
            </script>
            """)
    List<AgentVenueProductVO> selectOneHourAgentCandidates(@Param("sportCode") String sportCode,
                                                           @Param("placeRank") Integer placeRank,
                                                           @Param("targetDate") LocalDate targetDate,
                                                           @Param("startTime") LocalTime startTime,
                                                           @Param("windowEnd") LocalTime windowEnd,
                                                           @Param("maxBudget") BigDecimal maxBudget,
                                                           @Param("limit") int limit);

    @Select("""
            <script>
            select p.id as product_id, i.id as inventory_id, p.venue_id, p.amap_place_id,
                   p.venue_name, p.place_rank, p.sport_code, p.product_type, p.title,
                   p.description, p.cover_url, i.price, p.original_price, p.tags,
                   p.use_rule, p.refund_rule, i.service_date, i.start_time, i.end_time,
                   i.available_stock
            from venue p
            join venue_inventory i on i.product_id = p.id
            where p.status = 1 and i.status = '可售' and i.available_stock &gt; 0
              and p.sport_code = #{sportCode} and p.place_rank = #{placeRank}
              and (i.service_date &gt; current_date
                   or (i.service_date = current_date and i.start_time &gt; current_time))
            <if test="maxBudget != null">and i.price &lt;= #{maxBudget}</if>
            order by i.service_date, i.start_time, i.price, p.id
            limit #{limit}
            </script>
            """)
    List<AgentVenueProductVO> selectUpcomingAgentCandidates(@Param("sportCode") String sportCode,
                                                            @Param("placeRank") Integer placeRank,
                                                            @Param("maxBudget") BigDecimal maxBudget,
                                                            @Param("limit") int limit);

    @Select("""
            select p.id as product_id, i.id as inventory_id, p.venue_id, p.amap_place_id,
                   p.venue_name, p.title as product_title, p.product_type, i.service_date,
                   i.start_time, i.end_time, i.available_stock, i.price
            from venue p
            join venue_inventory i on i.product_id = p.id
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
    VenueItemSale selectSale(@Param("productId") Long productId, @Param("inventoryId") Long inventoryId);

    @Update("""
            update venue_inventory
            set available_stock = available_stock - 1,
                sold_stock = sold_stock + 1,
                updated_at = now()
            where id = #{inventoryId} and product_id = #{productId} and status = '可售' and available_stock > 0
            """)
    int deductInventory(@Param("productId") Long productId, @Param("inventoryId") Long inventoryId);

    @Insert("""
            insert into order_venue(user_id, product_id, inventory_id, venue_id, amap_place_id,
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
            from venue_inventory
            where product_id = #{productId} and status = '可售' and available_stock > 0 and service_date >= current_date
            order by service_date, start_time, id
            limit 1
            """)
    Long selectFirstAvailableInventoryId(@Param("productId") Long productId);

    @Update("""
            update order_venue
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

    @Select("select count(*) from cart_venue where user_id = #{userId} and product_id = #{productId} and inventory_id = #{inventoryId}")
    Integer countVenueCart(@Param("userId") Long userId,
                           @Param("productId") Long productId,
                           @Param("inventoryId") Long inventoryId);

    @Update("""
            update cart_venue
            set quantity = least(quantity + 1, 99), updated_at = now()
            where user_id = #{userId} and product_id = #{productId} and inventory_id = #{inventoryId}
            """)
    int touchVenueCart(@Param("userId") Long userId,
                       @Param("productId") Long productId,
                       @Param("inventoryId") Long inventoryId);

    @Insert("insert into cart_venue(user_id, product_id, inventory_id, quantity) values (#{userId}, #{productId}, #{inventoryId}, 1)")
    int insertVenueCart(@Param("userId") Long userId,
                        @Param("productId") Long productId,
                        @Param("inventoryId") Long inventoryId);

    @Select("""
            select c.id, p.id as product_id, i.id as inventory_id, p.title as product_name,
                   p.venue_name, p.cover_url, i.price, c.quantity, i.price * c.quantity as amount,
                   i.service_date, i.start_time, i.end_time
            from cart_venue c
            join venue p on p.id = c.product_id
            join venue_inventory i on i.id = c.inventory_id and i.product_id = c.product_id
            where c.user_id = #{userId}
            order by c.updated_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "product_id", javaType = Long.class),
            @Arg(column = "inventory_id", javaType = Long.class),
            @Arg(column = "product_name", javaType = String.class),
            @Arg(column = "venue_name", javaType = String.class),
            @Arg(column = "cover_url", javaType = String.class),
            @Arg(column = "price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class),
            @Arg(column = "amount", javaType = BigDecimal.class),
            @Arg(column = "service_date", javaType = LocalDate.class),
            @Arg(column = "start_time", javaType = LocalTime.class),
            @Arg(column = "end_time", javaType = LocalTime.class)
    })
    List<VenueCartItem> selectVenueCart(@Param("userId") Long userId);

    @Delete("delete from cart_venue where id = #{itemId} and user_id = #{userId}")
    int deleteVenueCart(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Update("""
            update cart_venue
            set quantity = #{quantity}, updated_at = now()
            where id = #{itemId} and user_id = #{userId}
            """)
    int updateVenueCartQuantity(@Param("userId") Long userId,
                                @Param("itemId") Long itemId,
                                @Param("quantity") Integer quantity);

    @Delete("delete from cart_venue where user_id = #{userId}")
    int deleteVenueCartByUser(@Param("userId") Long userId);

    @Update("""
            update order_venue
            set status = '已支付', paid_at = now(), updated_at = now()
            where id = #{orderId} and user_id = #{userId} and status = '待支付'
            """)
    int payVenueOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class VenueItemRow {
        private Long id;
        private Long venueId;
        private String amapPlaceId;
        private String venueName;
        private Integer placeRank;
        private String sportCode;
        private String productType;
        private String title;
        private String description;
        private String coverUrl;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private String tags;
        private String useRule;
        private String refundRule;
        private Integer availableStock;
        private LocalDateTime saleStartAt;
        private LocalDateTime saleEndAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class VenueItemSale {
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
        private Integer availableStock;
        private BigDecimal price;
    }

    @Data

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
    }
}


