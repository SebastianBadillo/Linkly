package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetUrlValidatorTest {

    private final TargetUrlValidator validator = new TargetUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#top",
            "HTTPS://Example.com/Black-Friday",
            "https://tienda.com/cañón"})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSpaces() {
        assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsEmpty(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsTooLong() {
        String url = "https://example.com/" + "a".repeat(TargetUrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("at most");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://example.com", "file:///etc/passwd", "example.com"})
    void rejectsOtherSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http");
    }

    @Test
    void rejectsMalformed() {
        assertThatThrownBy(() -> validator.validate("https://exa mple.com"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("well formed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https:example.com", "https:///path-only"})
    void rejectsMissingHost(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("host");
    }
}
