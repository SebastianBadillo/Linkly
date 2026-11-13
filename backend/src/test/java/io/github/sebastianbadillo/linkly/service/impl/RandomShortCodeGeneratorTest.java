package io.github.sebastianbadillo.linkly.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RandomShortCodeGeneratorTest {

    private final RandomShortCodeGenerator generator = new RandomShortCodeGenerator();

    @Test
    void generatesSixLowercaseBase36Characters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.generate()).matches("[a-z0-9]{6}");
        }
    }

    @Test
    void generatedCodesAreNotRepeated() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        // 1,000 codes out of 2.2 billion: a repeat is possible but extremely rare (~0.02%),
        // so we allow at most one instead of making the test flaky.
        assertThat(codes).hasSizeGreaterThanOrEqualTo(999);
    }
}
