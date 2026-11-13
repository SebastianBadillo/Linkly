package io.github.sebastianbadillo.linkly.service.impl;

import io.github.sebastianbadillo.linkly.service.ShortCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Random base36 codes (a-z, 0-9), 6 characters: 36^6 ≈ 2.2 billion possible codes.
 * SecureRandom so codes cannot be predicted from previous ones (see ADR 0001).
 */
@Component
public class RandomShortCodeGenerator implements ShortCodeGenerator {

    static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
