package com.personaltracker.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.domain.exception.InvalidDisplayNameException;
import com.personaltracker.identity.domain.exception.InvalidTimeZoneException;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserTests {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");

    @Test
    void registersWithTrimmedNameAndBogotaTimeZone() {
        User user = newUser("  Kevin  ");

        assertThat(user.displayName()).isEqualTo("Kevin");
        assertThat(user.timeZone()).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    void rejectsBlankOrTooLongNames() {
        assertThatThrownBy(() -> newUser("   ")).isInstanceOf(InvalidDisplayNameException.class);
        assertThatThrownBy(() -> newUser("x".repeat(101))).isInstanceOf(InvalidDisplayNameException.class);
    }

    @Test
    void locksAfterFiveFailedLoginsFor15Minutes() {
        User user = newUser("Kevin");

        for (int attempt = 1; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.recordFailedLogin(NOW);
            assertThat(user.isLocked(NOW)).isFalse();
        }
        user.recordFailedLogin(NOW);

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(User.LOCK_DURATION).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(NOW.plus(User.LOCK_DURATION))).isFalse();
    }

    @Test
    void restartsCountingAfterTheLockExpires() {
        User user = newUser("Kevin");
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.recordFailedLogin(NOW);
        }

        Instant afterLock = NOW.plus(User.LOCK_DURATION);
        user.recordFailedLogin(afterLock);

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLocked(afterLock)).isFalse();
    }

    @Test
    void successfulLoginResetsFailuresAndRecordsTheTime() {
        User user = newUser("Kevin");
        user.recordFailedLogin(NOW);
        user.recordFailedLogin(NOW);

        user.recordSuccessfulLogin(NOW);

        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void acceptsOnlyRegionTimeZones() {
        assertThat(User.parseTimeZone("America/Bogota")).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+05:00", "Marte/Base", "", "GMT-5"})
    void rejectsOffsetsAndUnknownZones(String value) {
        assertThatThrownBy(() -> User.parseTimeZone(value)).isInstanceOf(InvalidTimeZoneException.class);
    }

    private static User newUser(String displayName) {
        return User.register(UserId.newId(), new Email("kevin@example.com"), "hash", displayName, NOW);
    }
}
