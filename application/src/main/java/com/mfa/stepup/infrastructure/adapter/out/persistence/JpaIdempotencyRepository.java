package com.mfa.stepup.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfa.stepup.application.port.in.AuthorizeSensitiveOperationUseCase.AuthorizationResult;
import com.mfa.stepup.application.port.out.IdempotencyPort;
import com.mfa.stepup.domain.exception.IdempotencyConflictException;
import com.mfa.stepup.domain.model.IdempotencyRecord;
import com.mfa.stepup.domain.model.OperationId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JpaIdempotencyRepository implements IdempotencyPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JpaIdempotencyRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public Optional<Entry> find(String scope, String keyHash) {
        return jdbc.query("SELECT * FROM idempotency_records WHERE scope=? AND key_hash=?", (rs, row) -> {
            var record = new IdempotencyRecord(rs.getString("scope"), rs.getString("key_hash"), rs.getString("request_fingerprint"),
                IdempotencyRecord.State.valueOf(rs.getString("state")), new OperationId(rs.getString("operation_id")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("lease_until").toInstant(), rs.getString("owner_token"));
            try {
                String response = rs.getString("response_body");
                return new Entry(record, rs.getString("authorization_id"), response == null ? null : mapper.readValue(response, AuthorizationResult.class));
            } catch (JsonProcessingException ex) { throw new IllegalStateException("Stored idempotency result cannot be read", ex); }
        }, scope, keyHash).stream().findFirst();
    }

    public void create(Entry entry) {
        var r = entry.record();
        try {
            jdbc.update("""
                INSERT INTO idempotency_records(scope,key_hash,request_fingerprint,state,operation_id,
                    created_at,updated_at,expires_at,authorization_id,owner_token,lease_until)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, r.scope(),r.keyHash(),r.fingerprint(),r.state().name(),r.operationId().value(),Timestamp.from(r.createdAt()),
                Timestamp.from(r.updatedAt()),Timestamp.from(r.expiresAt()),entry.authorizationId(),r.ownerToken(),Timestamp.from(r.leaseUntil()));
        } catch (DuplicateKeyException ex) {
            // Propagate out of the preparation transaction; never query an aborted PostgreSQL transaction.
            throw new IdempotencyConflictException("Matching request was reserved concurrently");
        }
    }

    public boolean replaceIfOwned(Entry previous, Entry next) {
        var r = next.record();
        String body;
        try { body = next.result() == null ? null : mapper.writeValueAsString(next.result()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Idempotency result cannot be stored", ex); }
        return jdbc.update("""
            UPDATE idempotency_records SET state=?,updated_at=?,owner_token=?,lease_until=?,
                response_status=?,response_body=?,business_result_ref=?
            WHERE scope=? AND key_hash=? AND owner_token=? AND state=? AND request_fingerprint=?
            """, r.state().name(),Timestamp.from(r.updatedAt()),r.ownerToken(),Timestamp.from(r.leaseUntil()),
            next.result() == null ? null : next.result().outcomeUnknown() ? 202 : 200,body,
            next.result() == null ? null : next.result().businessResultRef(),
            r.scope(),r.keyHash(),previous.record().ownerToken(),previous.record().state().name(),r.fingerprint()) == 1;
    }
}
