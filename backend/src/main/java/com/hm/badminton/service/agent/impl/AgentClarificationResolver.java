package com.hm.badminton.service.agent.impl;

import com.hm.badminton.dto.agent.AgentRequirement;

import java.util.List;
import java.util.Map;

/** Builds deterministic clarification questions before incomplete requests reach business tools. */
public final class AgentClarificationResolver {

    private static final Map<String, String> SPORT_NAMES = Map.of(
            "badminton", "羽毛球",
            "table_tennis", "乒乓球",
            "football", "足球",
            "basketball", "篮球",
            "tennis", "网球",
            "volleyball", "排球"
    );

    private AgentClarificationResolver() {
    }

    /**
     * A request such as "按预算筛装备" is incomplete when neither this turn nor memory
     * contains a price ceiling. Asking for an amount is more useful than returning no data.
     */
    public static Clarification budget(String message, AgentRequirement requirement) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        boolean asksForBudgetFilter = text.matches(".*(按预算|预算筛选?|按价格|价格筛选?).*");
        if (!asksForBudgetFilter || requirement == null || requirement.getMaxBudget() != null
                || !hasEquipmentContext(text, requirement)) {
            return null;
        }

        String target = equipmentTarget(requirement);
        return new Clarification(
                "你希望预算控制在多少？请直接输入金额，例如“500元以内的" + target
                        + "”，或者点击下方预算选项。",
                List.of(
                        "200元以内的" + target,
                        "500元以内的" + target,
                        "1000元以内的" + target,
                        "不限预算的" + target));
    }

    /** Equipment is platform-wide. This fallback runs only after higher-priced peers were also checked. */
    public static Clarification equipmentNoResult(AgentRequirement requirement) {
        if (requirement == null || requirement.getIntents() == null
                || !requirement.getIntents().contains("EQUIPMENT")) {
            return null;
        }
        String target = equipmentTarget(requirement);
        if (requirement.getMaxBudget() == null) {
            return new Clarification(
                    "当前平台暂时没有找到符合条件的" + target
                            + "。装备商品不按地理距离筛选，请补充预算上限或更换装备类别。",
                    List.of(
                            "200元以内的" + target,
                            "500元以内的" + target,
                            "1000元以内的" + target,
                            "查看不限品类装备"));
        }

        int budget = requirement.getMaxBudget().intValue();
        return new Clarification(
                "当前平台没有找到" + budget + "元以内的" + target
                        + "，自动检查价格更高的同类商品后也没有可购买结果。你可以更换装备类别，我不会把装备请求切换成场馆团购。",
                List.of("查看不限品类装备", "推荐新手装备"));
    }

    private static boolean hasEquipmentContext(String message, AgentRequirement requirement) {
        boolean currentTurnMentionsEquipment = message.matches(
                ".*(装备|球拍|拍子|球鞋|运动鞋|鞋子|手胶|护具|球包|球袜).*"
        );
        boolean rememberedEquipmentIntent = requirement.getIntents() != null
                && requirement.getIntents().contains("EQUIPMENT");
        return currentTurnMentionsEquipment || rememberedEquipmentIntent
                || (requirement.getEquipmentKeyword() != null
                && !requirement.getEquipmentKeyword().isBlank());
    }

    private static String equipmentTarget(AgentRequirement requirement) {
        String sport = requirement.getSportCodes() == null ? "" : requirement.getSportCodes().stream()
                .map(SPORT_NAMES::get)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("");
        String keyword = requirement.getEquipmentKeyword();
        String category = keyword == null || keyword.isBlank() ? "装备" : categoryName(keyword);
        return sport + category;
    }

    private static String categoryName(String keyword) {
        return switch (keyword) {
            case "鞋" -> "鞋";
            case "球拍" -> "球拍";
            case "手胶" -> "手胶";
            case "护" -> "护具";
            case "包" -> "球包";
            case "袜" -> "球袜";
            default -> keyword;
        };
    }

    public record Clarification(String answer, List<String> quickReplies) {
    }
}
