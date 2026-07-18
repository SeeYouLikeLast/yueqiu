package com.hm.badminton.controller.catalog;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.entity.EquipmentCategory;
import com.hm.badminton.service.trade.IEquipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 按交易类型和运动查询商品分类。 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final IEquipmentService equipmentService;

    public CategoryController(IEquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping("/{type:[12]}")
    public ApiResponse<?> categories(@PathVariable Integer type, @RequestParam(required = false) String sport) {
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            return ApiResponse.ok(List.of(
                    Map.of("code", "ALL", "name", "全部"),
                    Map.of("code", "TIME_PACKAGE", "name", "畅打套餐"),
                    Map.of("code", "COURT_SLOT", "name", "单场时段"),
                    Map.of("code", "COACH_LESSON", "name", "私教课")
            ));
        }
        List<EquipmentCategory> categories = equipmentService.categories(sport);
        return ApiResponse.ok(categories);
    }
}
