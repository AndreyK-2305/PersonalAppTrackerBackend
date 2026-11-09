package com.personaltracker.capture.infrastructure.integration;

import com.personaltracker.capture.application.port.out.UserProducts;
import com.personaltracker.capture.domain.model.OwnedProduct;
import com.personaltracker.capture.domain.model.OwnedProductKind;
import com.personaltracker.finance.api.FinanceProducts;
import com.personaltracker.finance.api.ProductSummary;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Productos del usuario a través de {@code finance :: api}. */
@Component
class FinanceProductsAdapter implements UserProducts {

    private final FinanceProducts finance;

    FinanceProductsAdapter(FinanceProducts finance) {
        this.finance = finance;
    }

    @Override
    public List<OwnedProduct> activeProducts(UserId owner) {
        return finance.activeProducts(owner).stream().map(FinanceProductsAdapter::toOwnedProduct).toList();
    }

    @Override
    public UUID otherProduct(UserId owner) {
        return finance.otherProduct(owner);
    }

    private static OwnedProduct toOwnedProduct(ProductSummary product) {
        OwnedProductKind kind = switch (product.kind()) {
            case CASH -> OwnedProductKind.CASH;
            case DEPOSIT -> OwnedProductKind.DEPOSIT;
            case CREDIT_CARD -> OwnedProductKind.CREDIT_CARD;
        };
        return new OwnedProduct(product.id(), kind, Optional.ofNullable(product.institution()),
                Optional.ofNullable(product.lastFourDigits()));
    }
}
