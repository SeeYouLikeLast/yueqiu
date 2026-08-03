package com.hm.badminton;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.service.agent.tools.EquipmentAgentTool;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.ISeckillService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentAgentToolTest {

    @Test
    void strictBudgetSearchShouldIgnoreSoldOutItemsAndInspectFullCandidatePage() {
        IEquipmentService equipmentService = mock(IEquipmentService.class);
        ISeckillService seckillService = mock(ISeckillService.class);
        EquipmentAgentTool tool = new EquipmentAgentTool(equipmentService, seckillService);

        Equipment soldOut = equipment(1L, "售空排球训练鞋", "volleyball", 169, 0, "4.8");
        Equipment available = equipment(2L, "排球缓震训练鞋", "volleyball", 189, 8, "4.5");
        Equipment padded = equipment(3L, "补位乒乓球训练鞋", "table_tennis", 99, 20, "4.9");
        when(equipmentService.items("volleyball", null, "鞋", 1, 50))
                .thenReturn(new PageResult<>(List.of(soldOut, available, padded), 3, 1, 50));

        List<AgentCard> result = tool.searchEquipment("volleyball", "鞋", 200);

        assertThat(result).extracting(AgentCard::getTitle)
                .containsExactly("排球缓震训练鞋");
    }

    @Test
    void shouldReturnClosestPurchasableItemAboveBudget() {
        IEquipmentService equipmentService = mock(IEquipmentService.class);
        ISeckillService seckillService = mock(ISeckillService.class);
        EquipmentAgentTool tool = new EquipmentAgentTool(equipmentService, seckillService);

        Equipment shoe499 = equipment(2L, "进阶稳定羽毛球鞋", "badminton", 499, 12, "4.6");
        Equipment shoe329 = equipment(1L, "疾风缓震羽毛球鞋", "badminton", 329, 20, "4.5");
        Equipment padded = equipment(3L, "低价乒乓球鞋", "table_tennis", 209, 20, "4.9");
        when(equipmentService.items("badminton", null, "鞋", 1, 50))
                .thenReturn(new PageResult<>(List.of(shoe499, shoe329, padded), 3, 1, 50));

        List<AgentCard> result = tool.searchClosestAboveBudget("badminton", "鞋", 200);

        assertThat(result).hasSize(2);
        AgentCard closest = result.getFirst();
        assertThat(closest.getTitle()).isEqualTo("疾风缓震羽毛球鞋");
        assertThat(closest.getMeta())
                .containsEntry("budgetExpanded", true)
                .containsEntry("originalBudget", BigDecimal.valueOf(200))
                .containsEntry("budgetIncrease", BigDecimal.valueOf(129));
        List<String> reasons = ((List<?>) closest.getMeta().get("recommendReasons"))
                .stream()
                .map(String::valueOf)
                .toList();
        assertThat(reasons)
                .contains("只需增加¥129，是当前最接近原预算的同类商品")
                .doesNotContain("价格符合预算");
    }

    private Equipment equipment(Long id,
                                String name,
                                String sportCode,
                                int price,
                                int stock,
                                String score) {
        return new Equipment()
                .setId(id)
                .setSportCode(sportCode)
                .setCategoryId(2L)
                .setCategoryName("羽毛球鞋")
                .setName(name)
                .setBrand("FEATHERX")
                .setDescription("侧向支撑和缓震均衡，适合羽毛球训练。")
                .setCoverUrl("/objects/hm-badminton/demo/shoe.png")
                .setPrice(BigDecimal.valueOf(price))
                .setStock(stock)
                .setScore(new BigDecimal(score))
                .setSold(128)
                .setStatus(1);
    }
}
