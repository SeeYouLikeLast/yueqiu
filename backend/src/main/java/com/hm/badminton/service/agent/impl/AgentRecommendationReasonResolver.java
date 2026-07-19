package com.hm.badminton.service.agent.impl;

import com.hm.badminton.dto.agent.AgentCard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves one safe, card-specific recommendation reason for every selected card.
 * Model reasons take priority, while missing or duplicated model output falls back to
 * deterministic facts already present on the validated card.
 */
public final class AgentRecommendationReasonResolver {

    public static final String REASON_KEY = "recommendReason";
    public static final String REASON_SOURCE_KEY = "recommendReasonSource";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_RULE = "RULE";

    private AgentRecommendationReasonResolver() {
    }

    public static BindingResult bind(List<AgentCard> cards, Map<String, String> modelReasons) {
        Map<String, String> safeModelReasons = modelReasons == null ? Map.of() : modelReasons;
        Set<String> accepted = new HashSet<>();
        int aiCount = 0;
        int ruleCount = 0;

        for (AgentCard card : cards) {
            Map<String, Object> meta = ensureMeta(card);
            String reason = sanitize(safeModelReasons.get(card.getCardId()));
            String source = SOURCE_AI;
            if (reason.isBlank() || !accepted.add(normalize(reason))) {
                reason = uniqueFallbackReason(card, cards, accepted);
                source = SOURCE_RULE;
                ruleCount++;
            } else {
                aiCount++;
            }
            meta.put(REASON_KEY, reason);
            meta.put(REASON_SOURCE_KEY, source);
        }
        return new BindingResult(aiCount, ruleCount);
    }

    private static String uniqueFallbackReason(AgentCard card,
                                               List<AgentCard> cards,
                                               Set<String> accepted) {
        String reason = sanitize(fallbackReason(card, cards));
        if (reason.isBlank()) {
            reason = sanitize(subject(card) + "：信息与当前筛选条件匹配，可作为对比候选");
        }
        if (accepted.add(normalize(reason))) {
            return reason;
        }

        // The subject normally makes the reason unique even when products share the same rules.
        String qualified = sanitize(subject(card) + "：" + reason);
        if (accepted.add(normalize(qualified))) {
            return qualified;
        }
        String finalReason = sanitize(qualified + "，请结合该卡片价格与时段比较");
        accepted.add(normalize(finalReason));
        return finalReason;
    }

    private static String fallbackReason(AgentCard card, List<AgentCard> cards) {
        List<String> facts = new ArrayList<>();
        BigDecimal price = price(card);
        BigDecimal lowest = cards.stream()
                .filter(candidate -> sameType(card, candidate))
                .map(AgentRecommendationReasonResolver::price)
                .filter(value -> value != null)
                .min(BigDecimal::compareTo)
                .orElse(null);
        boolean hasHigherPrice = price != null && cards.stream()
                .filter(candidate -> sameType(card, candidate))
                .map(AgentRecommendationReasonResolver::price)
                .filter(value -> value != null)
                .anyMatch(value -> value.compareTo(price) > 0);

        if (price != null && lowest != null && price.compareTo(lowest) == 0 && hasHigherPrice) {
            facts.add("本次同类候选中价格最低（" + card.getPrice() + "）");
        } else if (price != null) {
            facts.add("价格为" + card.getPrice());
        }

        String timeMatch = metaText(card, "timeMatch");
        if (!timeMatch.isBlank()) {
            facts.add("时段" + timeMatch);
        }
        String distance = distanceText(card.getMeta() == null ? null : card.getMeta().get("distanceMeters"));
        if (!distance.isBlank()) {
            facts.add("距离约" + distance);
        }
        String feature = firstClause(card.getSubtitle());
        if (!feature.isBlank()) {
            facts.add("特点是" + feature);
        }
        if (facts.isEmpty()) {
            String fact = firstListText(card, "recommendReasons", "pros");
            if (!fact.isBlank()) {
                facts.add(fact);
            }
        }
        return subject(card) + "：" + String.join("，", facts.stream().limit(3).toList());
    }

    private static boolean sameType(AgentCard left, AgentCard right) {
        return left != null && right != null && java.util.Objects.equals(left.getType(), right.getType());
    }

    private static String subject(AgentCard card) {
        String venueName = metaText(card, "venueName");
        if (!venueName.isBlank() && !venueName.matches("\\d+")) {
            return venueName;
        }
        return card.getTitle() == null || card.getTitle().isBlank() ? "该候选" : card.getTitle().trim();
    }

    private static String firstClause(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clause = value.trim().split("[；;。.!！]", 2)[0].trim();
        return clause.length() <= 32 ? clause : clause.substring(0, 32);
    }

    private static String firstListText(AgentCard card, String... keys) {
        if (card.getMeta() == null) {
            return "";
        }
        for (String key : keys) {
            Object value = card.getMeta().get(key);
            if (value instanceof List<?> list) {
                String text = list.stream().map(String::valueOf)
                        .filter(item -> !item.isBlank())
                        .findFirst()
                        .orElse("");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static BigDecimal price(AgentCard card) {
        if (card == null || card.getPrice() == null || card.getPrice().isBlank()) {
            return null;
        }
        String number = card.getPrice().replaceAll("[^0-9.\\-]", "");
        if (number.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String distanceText(Object value) {
        if (!(value instanceof Number number)) {
            return "";
        }
        double meters = number.doubleValue();
        if (meters >= 1000) {
            return String.format(Locale.ROOT, "%.1fkm", meters / 1000D);
        }
        return Math.round(meters) + "m";
    }

    private static String metaText(AgentCard card, String key) {
        if (card == null || card.getMeta() == null) {
            return "";
        }
        Object value = card.getMeta().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Map<String, Object> ensureMeta(AgentCard card) {
        if (card.getMeta() == null) {
            card.setMeta(new LinkedHashMap<>());
        }
        return card.getMeta();
    }

    private static String sanitize(String reason) {
        if (reason == null) {
            return "";
        }
        String value = reason.replaceAll("[\\r\\n]+", " ")
                .replace("**", "")
                .replaceAll("\\s+", " ")
                .trim();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，,。.!！?？；;：:]", "");
    }

    public record BindingResult(int aiCount, int ruleCount) {
    }
}
