package com.wilson.spendify.finance;

import com.wilson.spendify.config.SpendifyProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CategorizationService {
    private static final Map<String, String> KEYWORDS = seededKeywords();

    private final MerchantAliasRuleRepository aliases;
    private final CategoryRepository categories;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final boolean aiEnabled;

    public CategorizationService(MerchantAliasRuleRepository aliases, CategoryRepository categories,
                                 ObjectProvider<ChatClient.Builder> chatClientBuilder,
                                 SpendifyProperties properties) {
        this.aliases = aliases;
        this.categories = categories;
        this.chatClientBuilder = chatClientBuilder;
        this.aiEnabled = properties.aiEnabled();
    }

    public Suggestion suggest(UUID userId, String merchant) {
        String normalized = normalize(merchant);
        MerchantAliasRule alias = aliases.findByUserIdAndNormalizedAlias(userId, normalized).orElse(null);
        if (alias != null) {
            return new Suggestion(alias.getCategoryId(), alias.getCanonicalMerchant(), 1.0, "Private merchant rule");
        }
        for (Map.Entry<String, String> entry : KEYWORDS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                Category category = categories.resolveByName(userId, entry.getValue()).orElseThrow();
                return new Suggestion(category.getId(), merchant, 0.85, "Seeded merchant keyword");
            }
        }
        Suggestion ai = suggestWithAi(userId, sanitizedForAi(merchant));
        if (ai != null) return ai;
        Category uncategorized = categories.resolveByName(userId, "Uncategorized").orElseThrow();
        return new Suggestion(uncategorized.getId(), merchant, 0.0, "No matching rule");
    }

    @Transactional
    public MerchantAliasRule learn(UUID userId, String alias, String canonicalMerchant, UUID categoryId) {
        Category category = categories.findById(categoryId)
                .filter(item -> item.getUserId() == null || item.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Category is not available to this user."));
        String normalized = normalize(alias);
        return aliases.findByUserIdAndNormalizedAlias(userId, normalized)
                .orElseGet(() -> aliases.save(new MerchantAliasRule(userId, normalized, canonicalMerchant,
                        category.getId())));
    }

    public String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private Suggestion suggestWithAi(UUID userId, String merchant) {
        ChatClient.Builder builder = aiEnabled ? chatClientBuilder.getIfAvailable() : null;
        if (builder == null || merchant.isBlank()) return null;
        String allowed = categories.availableTo(userId).stream().map(Category::getName)
                .reduce((left, right) -> left + ", " + right).orElse("Uncategorized");
        String response = builder.build().prompt()
                .system("Classify a sanitized merchant. Reply with one exact category name only.")
                .user("Merchant: " + merchant + "\nAllowed categories: " + allowed)
                .call().content();
        if (response == null) return null;
        return categories.resolveByName(userId, response.trim())
                .map(category -> new Suggestion(category.getId(), merchant, 0.65, "Optional AI suggestion"))
                .orElse(null);
    }

    private String sanitizedForAi(String merchant) {
        if (merchant == null) return "";
        String sanitized = merchant.replaceAll("(?i)\\b[A-Z0-9]{8,}\\b", " ")
                .replaceAll("[^A-Za-z0-9 '&.-]", " ").replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }

    private static Map<String, String> seededKeywords() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("grabfood", "Food & Dining");
        values.put("grab", "Transport");
        values.put("rapid kl", "Transport");
        values.put("petron", "Transport");
        values.put("shell", "Transport");
        values.put("99 speedmart", "Groceries");
        values.put("lotus", "Groceries");
        values.put("jaya grocer", "Groceries");
        values.put("restaurant", "Food & Dining");
        values.put("cafe", "Food & Dining");
        values.put("coffee", "Food & Dining");
        values.put("chicken", "Food & Dining");
        values.put("mcdonald", "Food & Dining");
        values.put("kfc", "Food & Dining");
        values.put("starbucks", "Food & Dining");
        values.put("tealive", "Food & Dining");
        values.put("familymart", "Food & Dining");
        values.put("mixue", "Food & Dining");
        values.put("foodpanda", "Food & Dining");
        values.put("bakery", "Food & Dining");
        values.put("kitchen", "Food & Dining");
        values.put("kopitiam", "Food & Dining");
        values.put("sushi", "Food & Dining");
        values.put("pizza", "Food & Dining");
        values.put("burger", "Food & Dining");
        values.put("mamak", "Food & Dining");
        values.put("aeon", "Groceries");
        values.put("mydin", "Groceries");
        values.put("supermarket", "Groceries");
        values.put("grocery", "Groceries");
        values.put("shopee", "Shopping");
        values.put("lazada", "Shopping");
        values.put("taobao", "Shopping");
        values.put("alipay", "Shopping");
        values.put("temu", "Shopping");
        values.put("uniqlo", "Shopping");
        values.put("mr diy", "Shopping");
        values.put("eco shop", "Shopping");
        values.put("maxis", "Bills & Utilities");
        values.put("celcom", "Bills & Utilities");
        values.put("digi", "Bills & Utilities");
        values.put("u mobile", "Bills & Utilities");
        values.put("guardian", "Health");
        values.put("watsons", "Health");
        values.put("pharmacy", "Health");
        values.put("clinic", "Health");
        values.put("hospital", "Health");
        values.put("university", "Education");
        values.put("college", "Education");
        values.put("udemy", "Education");
        values.put("coursera", "Education");
        values.put("bookstore", "Education");
        values.put("netflix", "Entertainment");
        values.put("spotify", "Entertainment");
        values.put("cinema", "Entertainment");
        values.put("gsc", "Entertainment");
        values.put("tgv", "Entertainment");
        values.put("steam", "Entertainment");
        values.put("unifi", "Bills & Utilities");
        values.put("tnb", "Bills & Utilities");
        return Collections.unmodifiableMap(values);
    }

    public record Suggestion(UUID categoryId, String canonicalMerchant, double confidence, String source) {}
}
