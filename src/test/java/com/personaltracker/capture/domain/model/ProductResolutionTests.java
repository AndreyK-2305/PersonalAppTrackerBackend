package com.personaltracker.capture.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductResolutionTests {

    private final UUID bancolombiaCard = UUID.randomUUID();
    private final UUID rappiCard = UUID.randomUUID();
    private final UUID bancolombiaSavings = UUID.randomUUID();

    private static OwnedProduct product(UUID id, OwnedProductKind kind, String institution, String digits) {
        return new OwnedProduct(id, kind, Optional.ofNullable(institution), Optional.ofNullable(digits));
    }

    private final List<OwnedProduct> products = List.of(
            product(bancolombiaCard, OwnedProductKind.CREDIT_CARD, "Bancolombia", "1234"),
            product(rappiCard, OwnedProductKind.CREDIT_CARD, "RappiCard", "1234"),
            product(bancolombiaSavings, OwnedProductKind.DEPOSIT, "bancolombia s.a.", "5678"));

    @Test
    void sameDigitsInDifferentInstitutionsAreDistinguished() {
        assertThat(ProductResolution.resolve(
                ProductHint.of(CaptureInstitution.BANCOLOMBIA, ProductType.CREDIT_CARD, "1234"), products))
                .isEqualTo(new ProductResolution.Matched(bancolombiaCard));
        assertThat(ProductResolution.resolve(
                ProductHint.withoutDigits(CaptureInstitution.RAPPI, ProductType.CREDIT_CARD), products))
                .isEqualTo(new ProductResolution.Matched(rappiCard));
    }

    @Test
    void theTypeMustMatch() {
        assertThat(ProductResolution.resolve(
                ProductHint.of(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT, "1234"), products))
                .isEqualTo(new ProductResolution.Unmatched());
    }

    @Test
    void withoutDigitsTheOnlyProductOfThatInstitutionAndTypeMatches() {
        assertThat(ProductResolution.resolve(
                ProductHint.withoutDigits(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT), products))
                .isEqualTo(new ProductResolution.Matched(bancolombiaSavings));
    }

    @Test
    void severalCandidatesAreAmbiguous() {
        List<OwnedProduct> twoAccounts = List.of(
                product(UUID.randomUUID(), OwnedProductKind.DEPOSIT, "Bancolombia", "1111"),
                product(UUID.randomUUID(), OwnedProductKind.DEPOSIT, "Bancolombia", "2222"));

        assertThat(ProductResolution.resolve(
                ProductHint.withoutDigits(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT), twoAccounts))
                .isEqualTo(new ProductResolution.Ambiguous());
    }

    @Test
    void productsWithoutInstitutionNeverMatch() {
        List<OwnedProduct> cash = List.of(product(UUID.randomUUID(), OwnedProductKind.CASH, null, null));

        assertThat(ProductResolution.resolve(
                ProductHint.withoutDigits(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT), cash))
                .isEqualTo(new ProductResolution.Unmatched());
    }
}
