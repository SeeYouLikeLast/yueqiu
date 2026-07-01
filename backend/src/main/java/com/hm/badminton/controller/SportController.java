package com.hm.badminton.controller;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.SportType;
import com.hm.badminton.service.ISportCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sports")
public class SportController {

    private final ISportCatalogService sportCatalogService;

    public SportController(ISportCatalogService sportCatalogService) {
        this.sportCatalogService = sportCatalogService;
    }

    @GetMapping
    public ApiResponse<List<SportType>> list() {
        return ApiResponse.ok(sportCatalogService.list());
    }
}

