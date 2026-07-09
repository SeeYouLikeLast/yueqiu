package com.hm.badminton.service.agent.tools;

import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentAction;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.ISeckillService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EquipmentAgentTool {

    private final IEquipmentService equipmentService;
    private final ISeckillService seckillService;

    public EquipmentAgentTool(IEquipmentService equipmentService, ISeckillService seckillService) {
        this.equipmentService = equipmentService;
        this.seckillService = seckillService;
    }

    @Tool(name = "searchEquipment", description = "Search equipment products by sport, keyword and max price.")
    public List<AgentCard> searchEquipment(String sportCode, String keyword, Integer maxPrice) {
        List<Equipment> items = equipmentService.items(blankToNull(sportCode), null, blankToNull(keyword), 1, 8).getRecords();
        List<AgentCard> cards = new ArrayList<>();
        for (Equipment item : items) {
            if (maxPrice != null && item.getPrice() != null
                    && item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) > 0) {
                continue;
            }
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_EQUIPMENT);
            card.setTitle(item.getName());
            card.setSubtitle(item.getDescription());
            card.setCoverUrl(item.getCoverUrl());
            card.setPrice(yuan(item.getPrice()));
            card.setTags(List.of(item.getBrand(), item.getCategoryName()));
            AgentAction action = AgentAction.of(AgentConstants.ACTION_OPEN_EQUIPMENT, item.getId());
            action.getPayload().put("sportCode", item.getSportCode());
            card.setAction(action);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", item.getId());
            meta.put("sportCode", item.getSportCode());
            meta.put("categoryId", item.getCategoryId());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    @Tool(name = "searchSeckillEquipment", description = "Search available seckill equipment products.")
    public List<AgentCard> searchSeckillEquipment(String sportCode) {
        List<SeckillActivity> activities = seckillService.list(2, blankToNull(sportCode), null);
        List<AgentCard> cards = new ArrayList<>();
        for (SeckillActivity activity : activities.stream().limit(6).toList()) {
            AgentCard card = new AgentCard();
            card.setType(AgentConstants.CARD_SECKILL);
            card.setTitle(activity.getProductName());
            card.setSubtitle("限时特价，库存以抢购时为准");
            card.setCoverUrl(activity.getCoverUrl());
            card.setPrice(yuan(activity.getSeckillPrice()));
            card.setTags(List.of(activity.getCategoryName(), "秒杀"));
            card.setAction(AgentAction.confirm(AgentConstants.ACTION_OPEN_EQUIPMENT, activity.getProductId()));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", activity.getId());
            meta.put("productId", activity.getProductId());
            meta.put("sportCode", activity.getSportCode());
            card.setMeta(meta);
            cards.add(card);
        }
        return cards;
    }

    private String yuan(BigDecimal value) {
        return value == null ? null : "¥" + value.stripTrailingZeros().toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
