package com.personaltracker.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.domain.exception.InvalidDeviceTokenException;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeviceTokenTests {

    private static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");

    private DeviceToken issue(String name, Set<DeviceScope> scopes) {
        return DeviceToken.issue(DeviceTokenId.newId(), UserId.newId(), name, "hash", scopes, NOW);
    }

    @Test
    void secretsAreRandomPrefixedAndOnlyTheirHashIsKept() {
        String first = DeviceTokenSecret.generate();
        String second = DeviceTokenSecret.generate();

        assertThat(first).startsWith(DeviceTokenSecret.PREFIX).isNotEqualTo(second);
        assertThat(DeviceTokenSecret.hash(first)).hasSize(64).isEqualTo(DeviceTokenSecret.hash(first))
                .isNotEqualTo(DeviceTokenSecret.hash(second)).doesNotContain(first);
    }

    @Test
    void aRevokedTokenAllowsNothingAndKeepsTheFirstRevocationDate() {
        DeviceToken token = issue("  iPhone  ", Set.of(DeviceScope.FINANCE_CAPTURE));
        assertThat(token.name()).isEqualTo("iPhone");
        assertThat(token.allows(DeviceScope.FINANCE_CAPTURE)).isTrue();

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(60));

        assertThat(token.allows(DeviceScope.FINANCE_CAPTURE)).isFalse();
        assertThat(token.revokedAt()).isEqualTo(NOW);
    }

    @Test
    void needsANameAndAtLeastOneScope() {
        assertThatThrownBy(() -> issue(" ", Set.of(DeviceScope.FINANCE_CAPTURE)))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> issue("x".repeat(61), Set.of(DeviceScope.FINANCE_CAPTURE)))
                .isInstanceOf(InvalidDeviceTokenException.class);
        assertThatThrownBy(() -> issue("iPhone", Set.of())).isInstanceOf(InvalidDeviceTokenException.class);
    }
}
