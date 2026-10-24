package com.mfa.stepup.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizeOperationCommand;
import com.mfa.stepup.application.port.out.RequestFingerprintPort;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
public class RequestFingerprinter implements RequestFingerprintPort {
    private final ObjectMapper mapper;
    public RequestFingerprinter(ObjectMapper mapper) { this.mapper = mapper; }

    public String fingerprint(AuthorizeOperationCommand command) {
        ObjectNode payload = mapper.valueToTree(command);
        payload.remove("correlationId"); payload.remove("idempotencyKey");
        return hash(canonical(payload));
    }

    public String scope(AuthorizeOperationCommand command) {
        ArrayNode scope = mapper.createArrayNode().add(command.subjectId()).add(command.actorId())
            .add(command.operationType()).add(command.operationId());
        return hash(scope);
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(e -> sorted.put(e.getKey(), canonical(e.getValue())));
            ObjectNode result = mapper.createObjectNode(); sorted.forEach(result::set); return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode(); node.forEach(v -> result.add(canonical(v))); return result;
        }
        return node;
    }

    private String hash(JsonNode node) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(node))); }
        catch (Exception ex) { throw new IllegalArgumentException("Request cannot be fingerprinted", ex); }
    }
}
