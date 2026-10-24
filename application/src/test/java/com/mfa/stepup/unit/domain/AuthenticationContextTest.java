package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.model.AuthenticationEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationContextTest {

    @Test
    @DisplayName("Should pass freshness check when authTime is within maxAge (RFC 9470 max_age)")
    void shouldValidateAuthenticationFreshness() {
        Instant now = Instant.now();
        Instant recentAuth = now.minusSeconds(60); // 1 minuto atrás
        Instant staleAuth = now.minusSeconds(600);  // 10 minutos atrás

        Duration maxAge = Duration.ofSeconds(300); // 5 minutos permitidos

        AuthenticationEvidence recentEvidence = new AuthenticationEvidence("mfa_hardware_token", recentAuth, "FIDO2");
        AuthenticationEvidence staleEvidence = new AuthenticationEvidence("mfa_hardware_token", staleAuth, "FIDO2");

        assertThat(recentEvidence.isFresh(maxAge, now)).isTrue();
        assertThat(staleEvidence.isFresh(maxAge, now)).isFalse();
    }

    @Test
    @DisplayName("Should evaluate authentication strength against required acr_values (RFC 9470 acr_values)")
    void shouldValidateAuthenticationStrength() {
        Instant now = Instant.now();
        AuthenticationEvidence strongEvidence = new AuthenticationEvidence("urn:mfa:strong", now, "FIDO2");
        AuthenticationEvidence passwordOnlyEvidence = new AuthenticationEvidence("urn:mfa:weak_pwd", now, "PASSWORD");

        assertThat(strongEvidence.satisfiesStrength("urn:mfa:strong")).isTrue();
        assertThat(passwordOnlyEvidence.satisfiesStrength("urn:mfa:strong")).isFalse();
        // Se a operação não exigir ACR específico, qualquer evidência válida satisfaz
        assertThat(passwordOnlyEvidence.satisfiesStrength(null)).isTrue();
        assertThat(passwordOnlyEvidence.satisfiesStrength("")).isTrue();
    }
}

