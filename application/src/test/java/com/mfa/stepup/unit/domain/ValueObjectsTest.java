package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.model.ActorId;
import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.model.IntentVersion;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    @DisplayName("SubjectId should enforce non-null and non-blank value")
    void shouldValidateSubjectId() {
        SubjectId subjectId = new SubjectId("usr_12345");
        assertThat(subjectId.value()).isEqualTo("usr_12345");

        assertThatThrownBy(() -> new SubjectId(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubjectId("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ActorId should enforce non-null and non-blank value")
    void shouldValidateActorId() {
        ActorId actorId = new ActorId("agent_ai_01");
        assertThat(actorId.value()).isEqualTo("agent_ai_01");

        assertThatThrownBy(() -> new ActorId(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActorId(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OperationId and OperationType should enforce non-null and non-blank")
    void shouldValidateOperationIdentifiers() {
        OperationId opId = new OperationId("op_789");
        OperationType opType = new OperationType("FUNDS_TRANSFER");

        assertThat(opId.value()).isEqualTo("op_789");
        assertThat(opType.value()).isEqualTo("FUNDS_TRANSFER");

        assertThatThrownBy(() -> new OperationId(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationType(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IntentDigest should enforce 64-character hexadecimal SHA-256 string")
    void shouldValidateIntentDigest() {
        String validHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        IntentDigest digest = new IntentDigest(validHash);
        assertThat(digest.value()).isEqualTo(validHash);

        assertThatThrownBy(() -> new IntentDigest("invalid_short_hash"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("64-character hexadecimal");
        assertThatThrownBy(() -> new IntentDigest(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("IntentVersion should enforce non-negative values")
    void shouldValidateIntentVersion() {
        IntentVersion version = new IntentVersion(1L);
        assertThat(version.value()).isEqualTo(1L);

        assertThatThrownBy(() -> new IntentVersion(-1L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

