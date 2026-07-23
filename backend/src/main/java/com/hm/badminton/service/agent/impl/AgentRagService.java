package com.hm.badminton.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.config.AgentProperties;
import com.hm.badminton.constants.AgentConstants;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.agent.AgentCard;
import com.hm.badminton.dto.agent.AgentRagEvidence;
import com.hm.badminton.dto.agent.AgentRequirement;
import com.hm.badminton.entity.Blog;
import com.hm.badminton.entity.VenueReview;
import com.hm.badminton.mapper.community.BlogMapper;
import com.hm.badminton.service.agent.IAgentRagService;
import com.hm.badminton.service.place.IVenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight hybrid RAG for the current small data set.
 *
 * <p>The source of truth remains MySQL. Venue reviews are retrieved only for the place
 * slots already returned by AMap; blogs are attached only to their related equipment or
 * venue product. DashScope embeddings are optional and cached in Redis. If embeddings
 * are disabled or fail, character/keyword retrieval still provides a deterministic
 * fallback without making hard availability claims.</p>
 */
@Service
public class AgentRagService implements IAgentRagService {

    private static final Logger log = LoggerFactory.getLogger(AgentRagService.class);
    private static final Set<String> SUBJECTIVE_WORDS = Set.of(
            "适合", "新手", "体验", "评价", "环境", "灯光", "地板", "地胶", "服务",
            "停车", "淋浴", "口碑", "心得", "好不好", "推荐", "稳定", "缓震", "实测");

    private final BlogMapper blogMapper;
    private final IVenueService venueService;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public AgentRagService(BlogMapper blogMapper,
                           IVenueService venueService,
                           ObjectProvider<EmbeddingModel> embeddingModelProvider,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           AgentProperties properties) {
        this.blogMapper = blogMapper;
        this.venueService = venueService;
        this.embeddingModelProvider = embeddingModelProvider;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<AgentCard> enrich(String query,
                                  String city,
                                  AgentRequirement requirement,
                                  List<AgentCard> cards) {
        if (!properties.isRagEnabled() || cards.isEmpty() || !shouldRetrieve(query, requirement)) {
            return cards;
        }
        List<RagDocument> documents = collectDocuments(city, requirement, cards);
        if (documents.isEmpty()) {
            return cards;
        }

        String retrievalQuery = buildRetrievalQuery(query, requirement);
        EmbeddingBundle embeddings = embeddings(retrievalQuery, documents);
        Map<String, List<ScoredDocument>> byCard = new HashMap<>();
        for (RagDocument document : documents) {
            double lexical = lexicalSimilarity(retrievalQuery, document.searchText());
            double semantic = embeddings.similarity(document.embeddingKey());
            double relevance = embeddings.enabled()
                    ? 0.30D * lexical + 0.70D * semantic
                    : lexical;
            // Every document is already entity-bound. The bonus keeps a short but exact
            // review useful even when the user's wording differs substantially.
            relevance = Math.min(1D, relevance + 0.20D);
            for (String cardId : document.targetCardIds()) {
                byCard.computeIfAbsent(cardId, ignored -> new ArrayList<>())
                        .add(new ScoredDocument(document, relevance));
            }
        }

        int evidenceCount = 0;
        int topK = Math.max(1, Math.min(properties.getRagTopK(), 5));
        for (AgentCard card : cards) {
            List<AgentRagEvidence> evidence = byCard.getOrDefault(card.getCardId(), List.of()).stream()
                    .sorted(java.util.Comparator.comparingDouble(ScoredDocument::score).reversed())
                    .limit(topK)
                    .map(ScoredDocument::toEvidence)
                    .toList();
            if (!evidence.isEmpty()) {
                Map<String, Object> meta = ensureMeta(card);
                meta.put("ragEvidence", evidence);
                meta.put("ragEvidenceCount", evidence.size());
                meta.put("knowledgeHighlights", evidence.stream()
                        .map(AgentRagEvidence::getExcerpt)
                        .toList());
                meta.put("knowledgeSources", evidence.stream()
                        .map(item -> item.getSourceType() + "#" + item.getSourceId() + " · " + item.getTitle())
                        .toList());
                meta.put("knowledgeScore", (int) Math.round(evidence.getFirst().getRelevance() * 100D));
                evidenceCount += evidence.size();
            }
        }
        log.info("agent.rag documents={} evidence={} mode={}", documents.size(), evidenceCount,
                embeddings.enabled() ? "hybrid" : "lexical");
        return cards;
    }

    private List<RagDocument> collectDocuments(String city,
                                               AgentRequirement requirement,
                                               List<AgentCard> cards) {
        Map<Long, List<String>> equipmentTargets = new LinkedHashMap<>();
        Map<Long, List<String>> venueProductTargets = new LinkedHashMap<>();
        Map<String, String> placeCardByPlaceId = new HashMap<>();
        for (AgentCard card : cards) {
            if (AgentConstants.CARD_EQUIPMENT.equals(card.getType())) {
                addTarget(equipmentTargets, actionId(card), card.getCardId());
            } else if (AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType())) {
                addTarget(venueProductTargets, actionId(card), card.getCardId());
            } else if (AgentConstants.CARD_PLACE.equals(card.getType()) && card.getAction() != null) {
                placeCardByPlaceId.put(card.getAction().getId(), card.getCardId());
            }
        }
        // A venue-product blog also enriches its concrete AMap place card.
        for (AgentCard card : cards) {
            if (!AgentConstants.CARD_VENUE_PRODUCT.equals(card.getType())) continue;
            Long productId = actionId(card);
            String placeId = Objects.toString(meta(card, "placeId"), "");
            String placeCardId = placeCardByPlaceId.get(placeId);
            if (productId != null && placeCardId != null) {
                venueProductTargets.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(placeCardId);
            }
        }

        List<RagDocument> documents = new ArrayList<>();
        List<String> sports = requirement.getSportCodes() == null ? List.of() : requirement.getSportCodes();
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .eq(Blog::getStatus, 1)
                .orderByDesc(Blog::getLiked)
                .orderByDesc(Blog::getCreatedAt)
                .last("limit " + Math.max(8, Math.min(properties.getRagCandidateLimit(), 200)));
        if (!sports.isEmpty()) wrapper.in(Blog::getSportCode, sports);
        for (Blog blog : blogMapper.selectList(wrapper)) {
            Map<Long, List<String>> targets = "EQUIPMENT".equals(blog.getRelatedType())
                    ? equipmentTargets
                    : "VENUE_PRODUCT".equals(blog.getRelatedType()) ? venueProductTargets : Map.of();
            List<String> cardIds = targets.get(blog.getRelatedId());
            if (cardIds == null || cardIds.isEmpty()) continue;
            documents.add(new RagDocument("博客", blog.getId(), blog.getTitle(),
                    blog.getTitle() + "。" + blog.getContent(), distinct(cardIds)));
        }

        for (AgentCard place : cards) {
            if (!AgentConstants.CARD_PLACE.equals(place.getType())) continue;
            Integer placeRank = integer(meta(place, "placeRank"));
            String sportCode = Objects.toString(meta(place, "sportCode"), "");
            if (placeRank == null || sportCode.isBlank()) continue;
            String placeCity = Objects.toString(meta(place, "city"), city);
            List<VenueReview> reviews = venueService.reviewsByPlaceSlot(placeCity, sportCode, placeRank, 1, 8);
            for (VenueReview review : reviews) {
                documents.add(new RagDocument("场馆评价", review.getId(), place.getTitle() + "的用户评价",
                        "评分" + review.getRating() + "分。" + review.getContent(), List.of(place.getCardId())));
            }
        }
        return documents;
    }

    private EmbeddingBundle embeddings(String query, List<RagDocument> documents) {
        EmbeddingModel model = embeddingModel();
        if (model == null) return EmbeddingBundle.disabled();
        try {
            LinkedHashMap<String, String> textByKey = new LinkedHashMap<>();
            String queryKey = hash(query);
            textByKey.put(queryKey, query);
            documents.forEach(document -> textByKey.putIfAbsent(document.embeddingKey(), document.searchText()));

            Map<String, float[]> vectors = new HashMap<>();
            List<String> missingKeys = new ArrayList<>();
            List<String> missingTexts = new ArrayList<>();
            textByKey.forEach((key, text) -> {
                float[] cached = readVector(key);
                if (cached == null) {
                    missingKeys.add(key);
                    missingTexts.add(text);
                } else {
                    vectors.put(key, cached);
                }
            });
            if (!missingTexts.isEmpty()) {
                List<float[]> embedded = model.embed(missingTexts);
                for (int index = 0; index < Math.min(embedded.size(), missingKeys.size()); index++) {
                    float[] vector = embedded.get(index);
                    vectors.put(missingKeys.get(index), vector);
                    cacheVector(missingKeys.get(index), vector);
                }
            }
            return new EmbeddingBundle(vectors.get(queryKey), vectors);
        } catch (RuntimeException ex) {
            log.warn("agent.rag embedding failed, using lexical retrieval: {}", ex.getMessage());
            return EmbeddingBundle.disabled();
        }
    }

    private EmbeddingModel embeddingModel() {
        if (!properties.isRagEmbeddingEnabled()) {
            return null;
        }
        try {
            return embeddingModelProvider.getIfAvailable();
        } catch (BeansException ex) {
            return null;
        }
    }

    private float[] readVector(String key) {
        try {
            String json = redisTemplate.opsForValue().get(RedisConstants.AGENT_RAG_EMBEDDING_KEY + key);
            return json == null || json.isBlank() ? null : objectMapper.readValue(json, float[].class);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return null;
        }
    }

    private void cacheVector(String key, float[] vector) {
        try {
            redisTemplate.opsForValue().set(RedisConstants.AGENT_RAG_EMBEDDING_KEY + key,
                    objectMapper.writeValueAsString(vector),
                    Duration.ofDays(Math.max(1, properties.getRagEmbeddingCacheDays())));
        } catch (RuntimeException | JsonProcessingException ignored) {
            // Retrieval remains valid without the cache; the next request may retry embedding.
        }
    }

    private boolean shouldRetrieve(String query, AgentRequirement requirement) {
        String text = Objects.toString(query, "");
        return SUBJECTIVE_WORDS.stream().anyMatch(text::contains)
                || requirement.getPreferenceTags() != null && !requirement.getPreferenceTags().isEmpty();
    }

    private String buildRetrievalQuery(String query, AgentRequirement requirement) {
        return Objects.toString(query, "") + " "
                + String.join(" ", requirement.getPreferenceTags() == null ? List.of() : requirement.getPreferenceTags())
                + " " + String.join(" ", requirement.getAvoidTags() == null ? List.of() : requirement.getAvoidTags());
    }

    private double lexicalSimilarity(String left, String right) {
        Set<String> queryTerms = terms(left);
        Set<String> documentTerms = terms(right);
        if (queryTerms.isEmpty() || documentTerms.isEmpty()) return 0D;
        long intersection = queryTerms.stream().filter(documentTerms::contains).count();
        return intersection / Math.sqrt((double) queryTerms.size() * documentTerms.size());
    }

    private Set<String> terms(String value) {
        String normalized = Objects.toString(value, "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ").trim();
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) continue;
            terms.add(token);
            if (token.codePoints().allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                int[] codePoints = token.codePoints().toArray();
                for (int index = 0; index < codePoints.length - 1; index++) {
                    terms.add(new String(codePoints, index, 2));
                }
            }
        }
        return terms;
    }

    private static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) return 0D;
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0D || rightNorm == 0D) return 0D;
        return Math.max(0D, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Long actionId(AgentCard card) {
        if (card.getAction() == null || card.getAction().getId() == null) return null;
        try {
            return Long.valueOf(card.getAction().getId());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void addTarget(Map<Long, List<String>> targets, Long id, String cardId) {
        if (id != null && cardId != null) targets.computeIfAbsent(id, ignored -> new ArrayList<>()).add(cardId);
    }

    private Object meta(AgentCard card, String key) {
        return card.getMeta() == null ? null : card.getMeta().get(key);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> ensureMeta(AgentCard card) {
        if (card.getMeta() == null) card.setMeta(new LinkedHashMap<>());
        return card.getMeta();
    }

    private List<String> distinct(Collection<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private record RagDocument(String sourceType,
                               Long sourceId,
                               String title,
                               String searchText,
                               List<String> targetCardIds) {
        private String embeddingKey() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(searchText.getBytes(StandardCharsets.UTF_8));
                return java.util.HexFormat.of().formatHex(digest);
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private record ScoredDocument(RagDocument document, double score) {
        private AgentRagEvidence toEvidence() {
            String text = document.searchText().replaceAll("\\s+", " ").trim();
            if (text.length() > 110) text = text.substring(0, 110) + "…";
            return new AgentRagEvidence(document.sourceType(), document.sourceId(), document.title(), text,
                    Math.round(score * 1000D) / 1000D);
        }
    }

    private record EmbeddingBundle(float[] query, Map<String, float[]> vectors) {
        private static EmbeddingBundle disabled() {
            return new EmbeddingBundle(null, Map.of());
        }

        private boolean enabled() {
            return query != null;
        }

        private double similarity(String key) {
            return cosine(query, vectors.get(key));
        }
    }
}
