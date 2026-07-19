package com.hm.badminton.service.agent.tools;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts conversational equipment requests into short database search terms.
 *
 * <p>The equipment query uses SQL {@code LIKE}. User wording such as "鞋子" does not
 * directly match a product named "羽毛球鞋", so category aliases must be normalized
 * before the keyword reaches the mapper.</p>
 */
public final class EquipmentQueryNormalizer {

    private static final List<Map.Entry<Pattern, String>> CATEGORY_ALIASES = List.of(
            alias("(球鞋|运动鞋|训练鞋|鞋子|鞋靴)", "鞋"),
            alias("(球拍|拍子)", "球拍"),
            alias("(手胶|握把胶|吸汗带)", "手胶"),
            alias("(护膝|护腕|护肘|护具)", "护"),
            alias("(球包|背包|包包)", "包"),
            alias("(球袜|运动袜|袜子)", "袜")
    );

    private EquipmentQueryNormalizer() {
    }

    public static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String compact = message.replaceAll("\\s+", "");
        for (Map.Entry<Pattern, String> alias : CATEGORY_ALIASES) {
            if (alias.getKey().matcher(compact).find()) {
                return alias.getValue();
            }
        }

        // Filtering instructions describe how to query; they are not product keywords.
        // Passing "按预算筛" into SQL LIKE would incorrectly produce an empty result set.
        if (compact.matches(".*(按预算|预算筛选?|按价格|价格筛选?).*")) {
            return null;
        }

        // "买羽毛球" means a consumable ball when no racket/shoe/etc. alias matched first.
        if (!compact.contains("装备")
                && compact.matches(".*(买|购买|来).*(羽毛球|乒乓球|足球|篮球|网球|排球).*$")) {
            return "球";
        }

        String text = message
                .replaceAll("(按预算(?:筛选?)?|预算筛选?|按价格(?:筛选?)?|价格筛选?|查看|推荐|帮我|我想要|我要|想要|来一个|来一双|找|购买|买|全部装备|不限装备类别|不限品类|装备|不限球类|不限|球类|以内|以下|左右|元|块|附近|有没有|可以|适合|新手|一个|一双|一件|一支|一副)", " ")
                .replaceAll("(羽毛球|羽毛|乒乓球|乒乓|足球|篮球|网球|排球)", " ")
                .replaceAll("[（）()，,。.!！?？、]", " ")
                .replaceAll("\\d+", " ")
                .replaceAll("\\s+", "")
                .trim();
        return text.isBlank() || text.length() > 12 ? null : text;
    }

    private static Map.Entry<Pattern, String> alias(String regex, String keyword) {
        return Map.entry(Pattern.compile(regex), keyword);
    }
}
