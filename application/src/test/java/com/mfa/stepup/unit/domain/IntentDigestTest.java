package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.service.IntentCanonicalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDigestTest {

    private final IntentCanonicalizer canonicalizer = new IntentCanonicalizer();

    @Test
    @DisplayName("Should produce identical SHA-256 digest regardless of key insertion order")
    void shouldProduceDeterministicDigestRegardlessOfKeyOrder() {
        // Map 1: insertion order A -> B -> C
        Map<String, Object> payload1 = new LinkedHashMap<>();
        payload1.put("amount", "1000.00");
        payload1.put("currency", "BRL");
        payload1.put("destinationAccount", "12345-6");
        payload1.put("sourceAccount", "98765-4");

        // Map 2: reverse insertion order
        Map<String, Object> payload2 = new LinkedHashMap<>();
        payload2.put("sourceAccount", "98765-4");
        payload2.put("destinationAccount", "12345-6");
        payload2.put("currency", "BRL");
        payload2.put("amount", "1000.00");

        IntentDigest digest1 = canonicalizer.calculateDigest(payload1);
        IntentDigest digest2 = canonicalizer.calculateDigest(payload2);

        assertThat(digest1).isNotNull();
        assertThat(digest2).isNotNull();
        assertThat(digest1).isEqualTo(digest2);
        assertThat(digest1.value()).hasSize(64);
    }

    @Test
    @DisplayName("Should produce different digest when any material field changes (FR-006 / SC-002)")
    void shouldProduceDifferentDigestWhenFieldChanges() {
        Map<String, Object> original = Map.of(
            "amount", "1000.00",
            "currency", "BRL",
            "destinationAccount", "12345-6"
        );

        // Altered amount
        Map<String, Object> altered = Map.of(
            "amount", "1000.01",
            "currency", "BRL",
            "destinationAccount", "12345-6"
        );

        IntentDigest digestOriginal = canonicalizer.calculateDigest(original);
        IntentDigest digestAltered = canonicalizer.calculateDigest(altered);

        assertThat(digestOriginal).isNotEqualTo(digestAltered);
    }
}

