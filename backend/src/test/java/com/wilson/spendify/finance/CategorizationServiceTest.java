package com.wilson.spendify.finance;

import com.wilson.spendify.config.SpendifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategorizationServiceTest {
    @Test
    void classifiesCommonMalaysianShoppingMerchantWithoutExternalAi() {
        UUID userId = UUID.randomUUID();
        MerchantAliasRuleRepository aliases = mock(MerchantAliasRuleRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> chat = mock(ObjectProvider.class);
        Category shopping = new Category(null, "Shopping", null);
        when(aliases.findByUserIdAndNormalizedAlias(userId, "shopeepay malaysia"))
                .thenReturn(Optional.empty());
        when(categories.resolveByName(userId, "Shopping")).thenReturn(Optional.of(shopping));
        CategorizationService service = new CategorizationService(aliases, categories, chat,
                new SpendifyProperties(null, null, false));

        CategorizationService.Suggestion result = service.suggest(userId, "ShopeePay Malaysia");

        assertThat(result.categoryId()).isEqualTo(shopping.getId());
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.source()).isEqualTo("Seeded merchant keyword");
    }

    @Test
    void classifiesGrabFoodAsDiningBeforeTheBroaderGrabTransportRule() {
        UUID userId = UUID.randomUUID();
        MerchantAliasRuleRepository aliases = mock(MerchantAliasRuleRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> chat = mock(ObjectProvider.class);
        Category dining = new Category(null, "Food & Dining", null);
        when(aliases.findByUserIdAndNormalizedAlias(userId, "grabfood malaysia"))
                .thenReturn(Optional.empty());
        when(categories.resolveByName(userId, "Food & Dining")).thenReturn(Optional.of(dining));
        CategorizationService service = new CategorizationService(aliases, categories, chat,
                new SpendifyProperties(null, null, false));

        CategorizationService.Suggestion result = service.suggest(userId, "GrabFood Malaysia");

        assertThat(result.categoryId()).isEqualTo(dining.getId());
    }
}
