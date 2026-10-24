package com.mfa.stepup.domain.model;

import com.mfa.stepup.domain.exception.InvalidIdempotencyKeyException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Retains only the hash; raw keys must never enter persistence or logs. */
public record IdempotencyKey(String hash) {
    public static IdempotencyKey from(String raw) {
        if (raw == null || !raw.matches("[\\x21-\\x7e]{16,255}"))
            throw new InvalidIdempotencyKeyException("Idempotency-Key must contain 16 to 255 printable non-space ASCII characters");
        try {
            return new IdempotencyKey(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
