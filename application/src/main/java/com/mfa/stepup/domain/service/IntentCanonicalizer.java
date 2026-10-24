package com.mfa.stepup.domain.service;

import com.mfa.stepup.domain.model.IntentDigest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Typed, length-prefixed canonical encoding: delimiters, nulls and whitespace cannot alias other intents. */
public class IntentCanonicalizer {
    public IntentDigest calculateDigest(Map<String, ?> materialData) {
        Objects.requireNonNull(materialData, "materialData cannot be null");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                text(output, "mfa-intent-v2");
                encode(output, materialData);
            }
            return new IntentDigest(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Canonical intent digest could not be calculated", ex);
        }
    }

    private void encode(DataOutputStream output, Object value) throws IOException {
        if (value == null) {
            output.writeByte(0);
        } else if (value instanceof String text) {
            output.writeByte(1);
            text(output, text);
        } else if (value instanceof Boolean flag) {
            output.writeByte(2);
            output.writeBoolean(flag);
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal
                || value instanceof Float number && Float.isFinite(number)
                || value instanceof Double number && Double.isFinite(number)) {
            output.writeByte(3);
            text(output, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Intent object keys must be strings");
                sorted.put(key, entry.getValue());
            }
            output.writeByte(4);
            output.writeInt(sorted.size());
            for (var entry : sorted.entrySet()) {
                text(output, entry.getKey());
                encode(output, entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            output.writeByte(5);
            output.writeInt(list.size());
            for (Object item : list) encode(output, item);
        } else {
            throw new IllegalArgumentException("Intent must contain only supported JSON values");
        }
    }

    private void text(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
