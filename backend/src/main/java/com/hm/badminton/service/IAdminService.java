package com.hm.badminton.service;

import java.util.List;
import java.util.Map;

public interface IAdminService {
    Map<String, Object> overview();

    List<Map<String, Object>> hotVenues();

    List<Map<String, Object>> hotProducts();

    List<Map<String, Object>> latestReviews();
}
