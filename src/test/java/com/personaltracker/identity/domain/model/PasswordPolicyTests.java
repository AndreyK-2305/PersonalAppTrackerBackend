package com.personaltracker.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.domain.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

    @Test
    void acceptsLengthsWithinLimits() {
        assertThatCode(() -> PasswordPolicy.check("a".repeat(12))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.check("a".repeat(64))).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortTooLongOrMissing() {
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(11)))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(65)))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.check(null))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void limitsBytesBecauseBcryptIgnoresWhatExceeds72() {
        // "ñ" ocupa 2 bytes en UTF-8: 36 caracteres = 72 bytes, 37 = 74 bytes.
        assertThatCode(() -> PasswordPolicy.check("ñ".repeat(36))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.check("ñ".repeat(37)))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }
}
