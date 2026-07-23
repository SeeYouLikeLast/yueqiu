package com.hm.badminton.service.agent.impl;

import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRequirement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies one explainable ranking model to every card type.
 *
 * <p>Tools remain responsible for hard facts and availability filtering. This scorer only
 * normalizes comparable signals and records a score breakdown; the LLM may explain or choose
 * validated card ids, but it cannot replace this backend ranking.</p>
 */
@Component
public class AgentRecommendationScorer {

    public List<AgentCard> scoreAndSort(List<AgentCard> cards, AgentRequirement requirement) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        RankingRange range = RankingRange.of(cards);
        Set<String> placesWithProducts = productPlaceIds(cards);
        cards.forEach(card -> score(card, requirement, range, placesWithProducts));
        return cards.stream()
                .sorted(Comparator.comparingInt(this::scoreOf).reversed()
                        .thenComparing(card -> card.getTitle() == null ? "" : card.getTitle()))
                .toList();
    }

    private void score(AgentCard card,
                       AgentRequirement requirement,
                       RankingRange range,
                       Set<String> placesWithProducts) {
        ScoreBuilder score = new ScoreBuilder();
        score.add("运动匹配", sportScore(card, requirement), 10);
        score.add("当前可用", availabilityScore(card, requirement, placesWithProducts), 20);
        score.add("预算匹配", budgetScore(card, requirement), 20);
        score.add("水平匹配", levelScore(card, requirement), 15);
        score.add("偏好匹配", preferenceScore(card, requirement), 15);
        score.add(typeFactorName(card), typeFactorScore(card, requirement), 20);
        score.add("检索口碑", knowledgeScore(card), 10);
        score.add("排序偏好", sortPreferenceScore(card, requirement, range), 10);

        int finalScore = score.percent();
        Map<String, Object> meta = ensureMeta(card);
        meta.put("recommendScore", finalScore);
        meta.put("scoreBreakdown", score.breakdown());
        meta.put("rankingVersion", "unified-v1");
        meta.put("recommendReasons", mergeReasons(score.reasons(requirement), meta.get("recommendReasons")));
    }

    private double sportScore(AgentCard card, AgentRequirement requirement) {
        List<String> sports = safeList(requirement.getSportCodes());
        if (sports.isEmpty()) {
            return 10;
        }
        String sport = textMeta(card, "sportCode");
        return sports.contains(sport) ? 10 : 0;
    }

    private double availabilityScore(AgentCard card,
                                     AgentRequirement requirement,
                                     Set<String> placesWithProducts) {
        if (AgentConstants.CARD_PLACE.equals(card.getType())) {
            String placeId = card.getAction() == null ? "" : nullToEmpty(card.getAction().getId());
            return placesWithProducts.contains(placeId) ? 20 : 10;
        }
        boolean available = booleanMeta(card, "available", true);
        if (!requirement.isAvailabilityRequired()) {
            return available ? 20 : 10;
        }
        return available ? 20 : 0;
    }

    private double budgetScore(AgentCard card, AgentRequirement requirement) {
        BigDecimal price = price(card);
        BigDecimal max = requirement.getMaxBudget();
        BigDecimal min = requirement.getMinBudget();
        if (max == null && min == null) {
            return price == null ? 14 : 17;
        }
        if (price == null) {
            return 8;
        }
        if (max != null && price.compareTo(max) > 0) {
            return 0;
        }
        if (min != null && price.compareTo(min) < 0) {
            return 15;
        }
        if (max == null || max.signum() <= 0) {
            return 20;
        }
        BigDecimal ratio = price.divide(max, 4, RoundingMode.HALF_UP);
        return Math.max(14, 20 - ratio.doubleValue() * 6);
    }

    private double levelScore(AgentCard card, AgentRequirement requirement) {
        String wanted = nullToEmpty(requirement.getLevel());
        if (wanted.isBlank() || wanted.contains("不限")) {
            return 15;
        }
        String suitable = textMeta(card, "suitableLevel");
        String searchable = searchableText(card);
        if (levelMatches(wanted, suitable) || levelMatches(wanted, searchable)) {
            return 15;
        }
        return suitable.isBlank() ? 9 : 4;
    }

    private double preferenceScore(AgentCard card, AgentRequirement requirement) {
        List<String> preferred = safeList(requirement.getPreferenceTags());
        List<String> avoided = safeList(requirement.getAvoidTags());
        if (preferred.isEmpty() && avoided.isEmpty()) {
            return 12;
        }
        String text = searchableText(card);
        long matched = preferred.stream().filter(text::contains).count();
        long conflicts = avoided.stream().filter(text::contains).count();
        double score = preferred.isEmpty() ? 13 : 5 + 10D * matched / preferred.size();
        return Math.max(0, Math.min(15, score - conflicts * 5));
    }

    private String typeFactorName(AgentCard card) {
        return switch (nullToEmpty(card.getType())) {
            case AgentConstants.CARD_PLACE -> "距离条件";
            case AgentConstants.CARD_VENUE_PRODUCT -> "时段条件";
            case AgentConstants.CARD_ACTIVITY -> "名额与时间";
            case AgentConstants.CARD_EQUIPMENT, AgentConstants.CARD_SECKILL -> "口碑与库存";
            default -> "业务匹配";
        };
    }

    private double typeFactorScore(AgentCard card, AgentRequirement requirement) {
        return switch (nullToEmpty(card.getType())) {
            case AgentConstants.CARD_PLACE -> distanceScore(card, requirement, 20);
            case AgentConstants.CARD_VENUE_PRODUCT -> timeScore(card, requirement, 20);
            case AgentConstants.CARD_ACTIVITY -> activityScore(card, requirement);
            case AgentConstants.CARD_EQUIPMENT, AgentConstants.CARD_SECKILL -> equipmentQualityScore(card);
            default -> 10;
        };
    }

    private double distanceScore(AgentCard card, AgentRequirement requirement, int maxPoints) {
        Double distance = numberMeta(card, "distanceMeters");
        if (distance == null) {
            return maxPoints * 0.5;
        }
        Integer limit = requirement.getMaxDistanceMeters();
        if (limit != null && distance > limit) {
            return 0;
        }
        double reference = limit == null ? 8000D : Math.max(500D, limit);
        return Math.max(2, maxPoints * (1D - Math.min(distance, reference) / reference));
    }

    private double timeScore(AgentCard card, AgentRequirement requirement, int maxPoints) {
        if (requirement.getStartTime() == null && requirement.getTargetDate() == null) {
            return maxPoints * 0.75;
        }
        String match = textMeta(card, "timeMatch");
        if (match.contains("完全")) return maxPoints;
        if (match.contains("单场一小时")) return maxPoints * 0.78;
        if (match.contains("最近")) return maxPoints * 0.58;
        return maxPoints * 0.35;
    }

    private double activityScore(AgentCard card, AgentRequirement requirement) {
        double time = requirement.getStartTime() == null ? 8 : 11;
        Double vacancy = numberMeta(card, "stock");
        double seats = vacancy == null ? 4 : vacancy >= 3 ? 9 : vacancy > 0 ? 6 : 0;
        return Math.min(20, time + seats);
    }

    private double equipmentQualityScore(AgentCard card) {
        Double rating = numberMeta(card, "rating");
        Double stock = numberMeta(card, "stock");
        double ratingPoints = rating == null ? 6 : Math.max(0, Math.min(10, rating / 5D * 10));
        double stockPoints = stock == null ? 5 : stock > 10 ? 10 : stock > 0 ? 7 : 0;
        return ratingPoints + stockPoints;
    }

    private double knowledgeScore(AgentCard card) {
        Double value = numberMeta(card, "knowledgeScore");
        return value == null ? 5 : Math.max(0, Math.min(10, value / 10D));
    }

    private double sortPreferenceScore(AgentCard card,
                                       AgentRequirement requirement,
                                       RankingRange range) {
        String preference = nullToEmpty(requirement.getSortPreference());
        if (preference.isBlank() || "BALANCED".equals(preference)) {
            return 7;
        }
        return switch (preference) {
            case "DISTANCE" -> inverseRange(numberMeta(card, "distanceMeters"), range.minDistance(), range.maxDistance());
            case "PRICE" -> inverseRange(decimal(price(card)), range.minPrice(), range.maxPrice());
            case "RATING" -> rangeScore(numberMeta(card, "rating"), range.minRating(), range.maxRating());
            case "TIME" -> timeScore(card, requirement, 10);
            case "VALUE" -> (inverseRange(decimal(price(card)), range.minPrice(), range.maxPrice())
                    + rangeScore(numberMeta(card, "rating"), range.minRating(), range.maxRating())) / 2D;
            default -> 7;
        };
    }

    private double inverseRange(Double value, Double min, Double max) {
        if (value == null || min == null || max == null || Objects.equals(min, max)) {
            return 6;
        }
        return 10D * (max - value) / (max - min);
    }

    private double rangeScore(Double value, Double min, Double max) {
        if (value == null || min == null || max == null || Objects.equals(min, max)) {
            return 6;
        }
        return 10D * (value - min) / (max - min);
    }

    private boolean levelMatches(String wanted, String text) {
        if (text == null || text.isBlank() || text.contains("不限") || text.contains("通用")) {
            return true;
        }
        if (wanted.contains("初") || wanted.contains("新手")) {
            return text.contains("初") || text.contains("新手") || text.contains("入门");
        }
        if (wanted.contains("中")) {
            return text.contains("中") || text.contains("进阶");
        }
        return text.contains("高") || text.contains("进阶") || text.contains("高手");
    }

    private Set<String> productPlaceIds(List<AgentCard> cards) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentCard card : cards) {
            if (AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType())) {
                String placeId = textMeta(card, "placeId");
                if (!placeId.isBlank()) ids.add(placeId);
            }
        }
        return ids;
    }

    private List<String> mergeReasons(List<String> scoredReasons, Object existing) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>(scoredReasons);
        if (existing instanceof List<?> values) {
            values.stream().map(String::valueOf).filter(value -> !value.isBlank()).forEach(reasons::add);
        }
        return reasons.stream().limit(4).toList();
    }

    private String searchableText(AgentCard card) {
        StringBuilder text = new StringBuilder();
        append(text, card.getTitle());
        append(text, card.getSubtitle());
        safeList(card.getTags()).forEach(value -> append(text, value));
        if (card.getMeta() != null) {
            for (String key : List.of("sceneTags", "pros", "cons", "knowledgeHighlights")) {
                Object value = card.getMeta().get(key);
                if (value instanceof List<?> list) list.forEach(item -> append(text, String.valueOf(item)));
                else append(text, value == null ? null : String.valueOf(value));
            }
        }
        return text.toString();
    }

    private void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) target.append(' ').append(value);
    }

    private int scoreOf(AgentCard card) {
        Double value = numberMeta(card, "recommendScore");
        return value == null ? 0 : value.intValue();
    }

    private Map<String, Object> ensureMeta(AgentCard card) {
        if (card.getMeta() == null) card.setMeta(new LinkedHashMap<>());
        return card.getMeta();
    }

    private String textMeta(AgentCard card, String key) {
        Object value = card.getMeta() == null ? null : card.getMeta().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanMeta(AgentCard card, String key, boolean fallback) {
        Object value = card.getMeta() == null ? null : card.getMeta().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private Double numberMeta(AgentCard card, String key) {
        Object value = card.getMeta() == null ? null : card.getMeta().get(key);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal price(AgentCard card) {
        if (card == null || card.getPrice() == null) return null;
        String value = card.getPrice().replaceAll("[^0-9.\\-]", "");
        try {
            return value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class ScoreBuilder {
        private final Map<String, Integer> breakdown = new LinkedHashMap<>();
        private double value;
        private double maximum;

        private void add(String name, double points, double max) {
            double safe = Math.max(0, Math.min(points, max));
            value += safe;
            maximum += max;
            breakdown.put(name, (int) Math.round(safe));
        }

        private int percent() {
            return maximum <= 0 ? 0 : (int) Math.round(value / maximum * 100D);
        }

        private Map<String, Integer> breakdown() {
            return breakdown;
        }

        private List<String> reasons(AgentRequirement requirement) {
            return breakdown.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 8)
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(entry -> reasonText(entry.getKey(), requirement))
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
        }

        private String reasonText(String key, AgentRequirement requirement) {
            return switch (key) {
                case "运动匹配" -> "运动类型匹配";
                case "当前可用" -> "当前仍可继续操作";
                case "预算匹配" -> requirement.getMaxBudget() == null ? "价格信息明确" : "符合预算上限";
                case "水平匹配" -> "适合当前水平";
                case "偏好匹配" -> "符合你关注的使用场景";
                case "距离条件" -> "距离更合适";
                case "时段条件", "名额与时间" -> "时间与可用条件更匹配";
                case "口碑与库存" -> "评分和可购买状态更稳妥";
                case "检索口碑" -> "有平台评价或使用心得支持";
                case "排序偏好" -> "更符合本轮排序偏好";
                default -> "";
            };
        }
    }

    private record RankingRange(Double minPrice,
                                Double maxPrice,
                                Double minDistance,
                                Double maxDistance,
                                Double minRating,
                                Double maxRating) {
        private static RankingRange of(List<AgentCard> cards) {
            List<Double> prices = cards.stream().map(AgentRecommendationScorer::staticPrice)
                    .filter(Objects::nonNull).toList();
            List<Double> distances = cards.stream().map(card -> staticNumber(card, "distanceMeters"))
                    .filter(Objects::nonNull).toList();
            List<Double> ratings = cards.stream().map(card -> staticNumber(card, "rating"))
                    .filter(Objects::nonNull).toList();
            return new RankingRange(min(prices), max(prices), min(distances), max(distances), min(ratings), max(ratings));
        }

        private static Double min(List<Double> values) {
            return values.stream().min(Double::compareTo).orElse(null);
        }

        private static Double max(List<Double> values) {
            return values.stream().max(Double::compareTo).orElse(null);
        }
    }

    private static Double staticPrice(AgentCard card) {
        if (card == null || card.getPrice() == null) return null;
        String value = card.getPrice().replaceAll("[^0-9.\\-]", "");
        try {
            return value.isBlank() ? null : Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double staticNumber(AgentCard card, String key) {
        Object value = card == null || card.getMeta() == null ? null : card.getMeta().get(key);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
