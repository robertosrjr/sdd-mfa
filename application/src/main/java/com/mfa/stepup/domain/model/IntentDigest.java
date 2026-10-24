package com.mfa.stepup.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Hash criptográfico (SHA-256) da representação canônica dos dados materiais da operação.
 */
public record IntentDigest(String value) {
    private static final Pattern HEX_64 = Pattern.compile("^[a-fA-F0-9]{64}$");

    public IntentDigest {
        Objects.requireNonNull(value, "intentDigest cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("intentDigest cannot be blank");
        }
        String normalized = value.trim().toLowerCase();
        if (!HEX_64.matcher(normalized).matches()) {
            throw new IllegalArgumentException("intentDigest must be a valid 64-character hexadecimal SHA-256 hash");
        }
        value = normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}

