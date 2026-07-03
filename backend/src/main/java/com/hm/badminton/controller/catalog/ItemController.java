package com.hm.badminton.controller.catalog;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.vo.EquipmentVO;
import com.hm.badminton.vo.VenueInventoryVO;
import com.hm.badminton.vo.VenueItemVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final IVenueItemService venueItemService;
    private final IEquipmentService equipmentService;

    public ItemController(IVenueItemService venueItemService, IEquipmentService equipmentService) {
        this.venueItemService = venueItemService;
        this.equipmentService = equipmentService;
    }

    @GetMapping("/{type:[12]}")
    public ApiResponse<?> items(@PathVariable Integer type,
                                @RequestParam(required = false) String sport,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String amapPlaceId,
                                @RequestParam(required = false) Long venueId,
                                @RequestParam(required = false) Integer placeRank,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "12") int size,
                                @RequestParam(defaultValue = "10") int limit) {
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            if (amapPlaceId != null || venueId != null || placeRank != null) {
                return ApiResponse.ok(venueItemService.items(sport, amapPlaceId, venueId, placeRank, limit)
                        .stream()
                        .map(VenueItemVO::from)
                        .toList());
            }
            var result = venueItemService.saleItems(sport, category, keyword, page, size);
            return ApiResponse.ok(new PageResult<>(
                    result.getRecords().stream().map(VenueItemVO::from).toList(),
                    result.getTotal(),
                    result.getPage(),
                    result.getSize()));
        }
        var result = equipmentService.items(sport, categoryId, keyword, page, size);
        return ApiResponse.ok(new PageResult<>(
                result.getRecords().stream().map(EquipmentVO::from).toList(),
                result.getTotal(),
                result.getPage(),
                result.getSize()));
    }

    @GetMapping("/{type:[12]}/{id}")
    public ApiResponse<?> detail(@PathVariable Integer type, @PathVariable Long id) {
        int tradeType = TradeType.require(type);
        return ApiResponse.ok(tradeType == TradeType.VENUE
                ? VenueItemVO.from(venueItemService.detail(id))
                : EquipmentVO.from(equipmentService.detail(id)));
    }

    @GetMapping("/1/{id}/inventories")
    public ApiResponse<List<VenueInventoryVO>> venueInventories(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(venueItemService.inventories(id, date)
                .stream()
                .map(VenueInventoryVO::from)
                .toList());
    }
}


