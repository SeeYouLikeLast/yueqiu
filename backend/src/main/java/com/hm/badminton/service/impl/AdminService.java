package com.hm.badminton.service.impl;

import com.hm.badminton.mapper.AdminMapper;
import com.hm.badminton.service.IAdminService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminService implements IAdminService {

    private static final Set<String> COUNT_TABLES = Set.of(
            "users", "venues", "products", "orders", "seckill_orders", "sport_activities", "venue_reviews");

    private final AdminMapper adminMapper;

    public AdminService(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    public Map<String, Object> overview() {
        return Map.of(
                "users", count("users"),
                "venues", count("venues"),
                "products", count("products"),
                "orders", count("orders"),
                "seckillOrders", count("seckill_orders"),
                "activities", count("sport_activities"),
                "reviews", count("venue_reviews"),
                "revenue", adminMapper.paidRevenue()
        );
    }

    public List<Map<String, Object>> hotVenues() {
        return adminMapper.hotVenues();
    }

    public List<Map<String, Object>> hotProducts() {
        return adminMapper.hotProducts();
    }

    public List<Map<String, Object>> latestReviews() {
        return adminMapper.latestReviews();
    }

    private Long count(String table) {
        if (!COUNT_TABLES.contains(table)) {
            throw new IllegalArgumentException("Unsupported table: " + table);
        }
        return adminMapper.countTable(table);
    }
}

