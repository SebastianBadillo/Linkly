package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.exception.InvalidAliasException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasValidatorTest {

    private final AliasValidator validator = new AliasValidator();

    @ParameterizedTest
    @ValueSource(strings = {"bf26", "black-friday_2026", "abc", "a23456789012345678901234567890"})
    void acceptsValidAliases(String alias) {
        assertThat(validator.normalize(alias)).isEqualTo(alias);
    }

    @Test
    void lowercasesAndTrims() {
        assertThat(validator.normalize("  BF26 ")).isEqualTo("bf26");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsEmpty(String alias) {
        assertThatThrownBy(() -> validator.normalize(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a234567890123456789012345678901"}) // 2 and 31 chars
    void rejectsWrongLength(String alias) {
        assertThatThrownBy(() -> validator.normalize(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("between");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bf/26", "bf?26", "bf 26", "bf#26", "cañón", "аpple" /* Cyrillic 'а' */})
    void rejectsUnsafeCharacters(String alias) {
        assertThatThrownBy(() -> validator.normalize(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("only contain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "actuator", "LOGIN", "Admin"})
    void rejectsReservedWordsInAnyCase(String alias) {
        assertThatThrownBy(() -> validator.normalize(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("reserved");
    }
}
