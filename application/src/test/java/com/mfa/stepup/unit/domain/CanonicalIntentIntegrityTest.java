package com.mfa.stepup.unit.domain;

import com.mfa.stepup.domain.service.IntentCanonicalizer;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CanonicalIntentIntegrityTest {
    private final IntentCanonicalizer canonicalizer=new IntentCanonicalizer();
    @Test void delimitersAndTypesCannotSubstituteAnotherIntent() {
        assertThat(canonicalizer.calculateDigest(Map.of("a","1|b:2")))
            .isNotEqualTo(canonicalizer.calculateDigest(Map.of("a","1","b","2")));
        assertThat(canonicalizer.calculateDigest(Map.of("amount","1")))
            .isNotEqualTo(canonicalizer.calculateDigest(Map.of("amount",1)));
    }
    @Test void nullAndWhitespaceAreMaterialData() {
        var explicitNull=new LinkedHashMap<String,Object>(); explicitNull.put("a",null);
        assertThat(canonicalizer.calculateDigest(explicitNull)).isNotEqualTo(canonicalizer.calculateDigest(Map.of()));
        assertThat(canonicalizer.calculateDigest(Map.of("a"," 1"))).isNotEqualTo(canonicalizer.calculateDigest(Map.of("a","1")));
        assertThat(canonicalizer.calculateDigest(Map.of(" a","1"))).isNotEqualTo(canonicalizer.calculateDigest(Map.of("a","1")));
    }
    @Test void sortsNestedObjectsAndPreservesArrayOrder() {
        var nested=new LinkedHashMap<String,Object>(); nested.put("b",2); nested.put("a",1);
        assertThat(canonicalizer.calculateDigest(Map.of("nested",nested)))
            .isEqualTo(canonicalizer.calculateDigest(Map.of("nested",Map.of("a",1,"b",2))));
        assertThat(canonicalizer.calculateDigest(Map.of("items",List.of(1,2))))
            .isNotEqualTo(canonicalizer.calculateDigest(Map.of("items",List.of(2,1))));
    }
    @Test void rejectsUnsupportedValuesInsteadOfHashingArbitraryToString() {
        assertThatThrownBy(() -> canonicalizer.calculateDigest(Map.of("value",new Object())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
