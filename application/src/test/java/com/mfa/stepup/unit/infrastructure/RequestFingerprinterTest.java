package com.mfa.stepup.unit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.infrastructure.adapter.in.web.RequestFingerprinter;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RequestFingerprinterTest {
    private final RequestFingerprinter hasher = new RequestFingerprinter(new ObjectMapper());
    private AuthorizeOperationCommand command(Map<String,Object> data, String correlation) {
        return new AuthorizeOperationCommand("owner", "agent", "op", "TRANSFER", 1, data, "approval", correlation, "a-long-idempotency-key");
    }
    @Test void canonicalizesNestedObjectsAndIgnoresCorrelation() {
        var nested = new LinkedHashMap<String,Object>(); nested.put("z", 1); nested.put("a", 2);
        assertThat(hasher.fingerprint(command(Map.of("nested", nested), "one")))
            .isEqualTo(hasher.fingerprint(command(Map.of("nested", Map.of("a",2,"z",1)), "two")));
    }
    @Test void distinguishesTypesDelimitersNullsAndWhitespace() {
        assertThat(hasher.fingerprint(command(Map.of("amount", 1), "one")))
            .isNotEqualTo(hasher.fingerprint(command(Map.of("amount", "1"), "one")));
        assertThat(hasher.fingerprint(command(Map.of("x", "a|b:c"), "one")))
            .isNotEqualTo(hasher.fingerprint(command(Map.of("x", "a", "b", "c"), "one")));
        var withNull = new LinkedHashMap<String,Object>(); withNull.put("x", null);
        assertThat(hasher.fingerprint(command(withNull,"one"))).isNotEqualTo(hasher.fingerprint(command(Map.of(),"one")));
        assertThat(hasher.fingerprint(command(Map.of("x"," a"),"one"))).isNotEqualTo(hasher.fingerprint(command(Map.of("x","a"),"one")));
    }
    @Test void fingerprintsAttributionApprovalAndVersionButScopesByClientAndOperation() {
        var original = command(Map.of("amount",1),"one");
        var altered = new AuthorizeOperationCommand("owner","agent","op","TRANSFER",2,original.materialData(),"other-approval","one",original.idempotencyKey());
        assertThat(hasher.scope(original)).isEqualTo(hasher.scope(altered));
        assertThat(hasher.fingerprint(original)).isNotEqualTo(hasher.fingerprint(altered));
        var otherActor = new AuthorizeOperationCommand("owner","other","op","TRANSFER",1,original.materialData(),"approval","one",original.idempotencyKey());
        assertThat(hasher.scope(original)).isNotEqualTo(hasher.scope(otherActor));
    }
}
