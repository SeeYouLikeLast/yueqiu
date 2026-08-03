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
import java.util.Comparator;
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
        String requestedSport = blankToNull(sportCode);
        // 先取完整的小型商品候选集再按预算过滤，避免前 8 条都超预算时误判为“没有商品”。
        List<Equipment> items = equipmentService.items(
                requestedSport, null, blankToNull(keyword), 1, 50).getRecords();
        List<AgentCard> cards = new ArrayList<>();
        for (Equipment item : items) {
            // 商城列表可能用其他球类补足一页；Agent 必须重新校验球类，不能把补位商品当成候选。
            if (requestedSport != null && !requestedSport.equals(item.getSportCode())) {
                continue;
            }
            // 推荐卡片必须可以立即购买，零库存商品不参与严格匹配或后续预算判断。
            if (item.getStock() == null || item.getStock() <= 0) {
                continue;
            }
            if (maxPrice != null && item.getPrice() != null
                    && item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) > 0) {
                continue;
            }
            cards.add(toCard(item, maxPrice));
        }
        return cards;
    }

    /**
     * 严格预算没有结果时，只在同一球类和同一装备类别中查找价格最接近的真实商品。
     * 原预算和需要增加的金额随卡片返回，回答层据此一次性说明预算放宽结果。
     */
    @Tool(name = "searchClosestEquipmentAboveBudget",
            description = "Search the closest purchasable equipment above the user's budget.")
    public List<AgentCard> searchClosestAboveBudget(String sportCode, String keyword, Integer maxPrice) {
        if (maxPrice == null || maxPrice < 0) {
            return List.of();
        }
        String requestedSport = blankToNull(sportCode);
        BigDecimal budget = BigDecimal.valueOf(maxPrice);
        return equipmentService.items(requestedSport, null, blankToNull(keyword), 1, 50)
                .getRecords()
                .stream()
                .filter(item -> requestedSport == null || requestedSport.equals(item.getSportCode()))
                .filter(item -> item.getPrice() != null && item.getPrice().compareTo(budget) > 0)
                .filter(item -> item.getStock() != null && item.getStock() > 0)
                .sorted(Comparator.comparing(Equipment::getPrice)
                        .thenComparing(Equipment::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(2)
                .map(item -> {
                    AgentCard card = toCard(item, maxPrice);
                    BigDecimal increase = item.getPrice().subtract(budget);
                    card.getMeta().put("budgetExpanded", true);
                    card.getMeta().put("originalBudget", budget);
                    card.getMeta().put("budgetIncrease", increase);

                    List<String> reasons = new ArrayList<>();
                    reasons.add("只需增加¥" + increase.stripTrailingZeros().toPlainString()
                            + "，是当前最接近原预算的同类商品");
                    reasons.addAll(pros(item, maxPrice));
                    card.getMeta().put("recommendReasons", reasons.stream().distinct().limit(3).toList());
                    return card;
                })
                .toList();
    }

    private AgentCard toCard(Equipment item, Integer maxPrice) {
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
        meta.put("available", item.getStock() != null && item.getStock() > 0);
        meta.put("stock", item.getStock());
        meta.put("rating", item.getScore());
        meta.put("sold", item.getSold());
        meta.put("suitableLevel", suitableLevel(item));
        meta.put("sceneTags", sceneTags(item));
        meta.put("pros", pros(item, maxPrice));
        meta.put("cons", cons(item));
        meta.put("recommendReasons", pros(item, maxPrice).stream().limit(3).toList());
        card.setMeta(meta);
        return card;
    }

    private String suitableLevel(Equipment item) {
        if (item.getPrice() == null) {
            return "通用";
        }
        if (item.getPrice().compareTo(BigDecimal.valueOf(150)) <= 0) {
            return "初学者";
        }
        if (item.getPrice().compareTo(BigDecimal.valueOf(500)) <= 0) {
            return "进阶";
        }
        return "高强度/进阶";
    }

    private List<String> sceneTags(Equipment item) {
        List<String> tags = new ArrayList<>();
        tags.add(nullToText(item.getBrand()));
        tags.add(nullToText(item.getCategoryName()));
        String text = (item.getName() + " " + item.getDescription()).toLowerCase();
        if (text.contains("轻") || text.contains("light")) {
            tags.add("轻量");
        }
        if (text.contains("稳") || text.contains("stable")) {
            tags.add("稳定");
        }
        if (text.contains("控") || text.contains("control")) {
            tags.add("控制");
        }
        return tags.stream().distinct().limit(6).toList();
    }

    private List<String> pros(Equipment item, Integer maxPrice) {
        List<String> pros = new ArrayList<>();
        if (item.getPrice() != null && (maxPrice == null || item.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0)) {
            pros.add("价格符合预算");
        }
        if (item.getScore() != null && item.getScore().compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            pros.add("评分较高");
        }
        if (item.getSold() != null && item.getSold() >= 100) {
            pros.add("销量反馈多");
        }
        if (item.getStock() != null && item.getStock() > 10) {
            pros.add("库存充足");
        }
        if (pros.isEmpty()) {
            pros.add("可作为当前运动的备选装备");
        }
        return pros;
    }

    private List<String> cons(Equipment item) {
        List<String> cons = new ArrayList<>();
        if (item.getStock() != null && item.getStock() <= 5) {
            cons.add("库存偏少");
        }
        if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.valueOf(500)) > 0) {
            cons.add("价格偏高，更适合有明确需求的用户");
        }
        if (item.getScore() != null && item.getScore().compareTo(BigDecimal.valueOf(4.2)) < 0) {
            cons.add("评分一般，建议对比后再买");
        }
        return cons;
    }

    private List<String> seckillPros(SeckillActivity activity) {
        List<String> pros = new ArrayList<>();
        if (activity.getOriginalPrice() != null && activity.getSeckillPrice() != null
                && activity.getOriginalPrice().compareTo(activity.getSeckillPrice()) > 0) {
            pros.add("限时价低于原价");
        }
        if (activity.getStock() != null && activity.getStock() > 0) {
            pros.add("当前可抢购");
        }
        return pros;
    }

    private List<String> seckillCons(SeckillActivity activity) {
        List<String> cons = new ArrayList<>();
        if (activity.getStock() != null && activity.getStock() <= 5) {
            cons.add("库存紧张");
        }
        cons.add("需要在活动时间内完成抢购");
        return cons;
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
            meta.put("available", activity.getStock() != null && activity.getStock() > 0);
            meta.put("stock", activity.getStock());
            meta.put("suitableLevel", "限时抢购");
            meta.put("sceneTags", List.of(nullToText(activity.getCategoryName()), "限时价"));
            meta.put("pros", seckillPros(activity));
            meta.put("cons", seckillCons(activity));
            meta.put("recommendReasons", seckillPros(activity).stream().limit(3).toList());
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

    private String nullToText(String value) {
        return value == null || value.isBlank() ? "未分类" : value;
    }
}
