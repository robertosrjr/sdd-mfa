package com.mfa.stepup.infrastructure.observability;

import com.mfa.stepup.domain.model.AuthorizationState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/** Allowlisted audit vocabulary and keyed, non-reversible correlation references. */
@Component
public class AuditPrivacyPolicy {
    private static final Set<String> EVENTS = Set.of("AUTHENTICATION_VERIFIED", "POLICY_VERIFIED",
        "AUTHORIZATION_REJECTED", "OPERATION_EXECUTED", "OUTCOME_UNKNOWN", "EXECUTION_FAILED",
        "TEMPORARY_LOCKOUT_HIT", "OPERATION_RECONCILED", "APPROVAL_CONSUMPTION_FAILED");
    private static final Set<String> OPERATIONS = Set.of("TRANSFER", "PIX_TRANSFER", "FUNDS_TRANSFER");
    private static final Set<String> DECISIONS = Set.of("ALLOWED", "BLOCKED", "FAILED", "UNKNOWN");
    private final byte[] key;

    public AuditPrivacyPolicy(@Value("${mfa.audit.pseudonym-key:}") String encodedKey) {
        if (encodedKey.isBlank()) {
            key = new byte[32]; new SecureRandom().nextBytes(key);
        } else {
            try { key = Base64.getDecoder().decode(encodedKey); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Audit key must be Base64 encoded"); }
            if (key.length < 32) throw new IllegalArgumentException("Audit key must contain at least 32 bytes");
        }
    }

    public String reference(String value) {
        if (value == null) return "NONE";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Audit references cannot be generated", ex); }
    }
    public String eventType(String value) { return EVENTS.contains(value) ? value : "OTHER"; }
    public static String operation(String value) { return value != null && OPERATIONS.contains(value) ? value : "OTHER"; }
    public String decision(String value) {
        if (DECISIONS.contains(value)) return value;
        try { return AuthorizationState.valueOf(value).name(); }
        catch (IllegalArgumentException | NullPointerException ex) { return "OTHER"; }
    }
}
