package com.mfa.stepup.infrastructure.adapter.out.persistence;

import com.mfa.stepup.application.port.out.OperationAuthorizationRepositoryPort;
import com.mfa.stepup.domain.model.ActorId;
import com.mfa.stepup.domain.model.AuthorizationState;
import com.mfa.stepup.domain.model.IntentDigest;
import com.mfa.stepup.domain.model.IntentVersion;
import com.mfa.stepup.domain.model.OperationAuthorization;
import com.mfa.stepup.domain.model.OperationId;
import com.mfa.stepup.domain.model.OperationType;
import com.mfa.stepup.domain.model.SubjectId;
import com.mfa.stepup.domain.model.TransactionApproval;
import com.mfa.stepup.domain.model.ApprovalDecision;
import com.mfa.stepup.domain.model.DelegationAttribution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adaptador de persistência relacional para OperationAuthorization.
 * Implementa consumo atômico Compare-And-Swap (CAS) garantindo uso estritamente único da aprovação.
 */
@Repository
public class JpaOperationAuthorizationRepository implements OperationAuthorizationRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JpaOperationAuthorizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public OperationAuthorization save(OperationAuthorization auth) {
        Objects.requireNonNull(auth, "auth cannot be null");

        String sql = """
            MERGE INTO operation_authorizations KEY(authorization_id) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

        // Compatibilidade PostgreSQL / H2
        try {
            jdbcTemplate.update(
                """
                INSERT INTO operation_authorizations (
                    authorization_id, operation_id, operation_type, subject_id, actor_id,
                    intent_version, intent_digest, policy_version, state, created_at, expires_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (authorization_id) DO UPDATE SET
                    state = EXCLUDED.state,
                    completed_at = EXCLUDED.completed_at
                """,
                auth.getAuthorizationId(),
                auth.getOperationId().value(),
                auth.getOperationType().value(),
                auth.getSubjectId().value(),
                auth.getActorId() != null ? auth.getActorId().value() : null,
                auth.getIntentVersion().value(),
                auth.getIntentDigest().value(),
                auth.getPolicyVersion(),
                auth.getState().name(),
                Timestamp.from(auth.getCreatedAt()),
                Timestamp.from(auth.getExpiresAt()),
                auth.getCompletedAt() != null ? Timestamp.from(auth.getCompletedAt()) : null
            );
        } catch (Exception pgEx) {
            // Fallback para H2 MERGE
            jdbcTemplate.update(
                """
                MERGE INTO operation_authorizations (
                    authorization_id, operation_id, operation_type, subject_id, actor_id,
                    intent_version, intent_digest, policy_version, state, created_at, expires_at, completed_at
                ) KEY (authorization_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                auth.getAuthorizationId(),
                auth.getOperationId().value(),
                auth.getOperationType().value(),
                auth.getSubjectId().value(),
                auth.getActorId() != null ? auth.getActorId().value() : null,
                auth.getIntentVersion().value(),
                auth.getIntentDigest().value(),
                auth.getPolicyVersion(),
                auth.getState().name(),
                Timestamp.from(auth.getCreatedAt()),
                Timestamp.from(auth.getExpiresAt()),
                auth.getCompletedAt() != null ? Timestamp.from(auth.getCompletedAt()) : null
            );
        }

        return auth;
    }

    @Override
    public Optional<OperationAuthorization> findById(String authorizationId) {
        String sql = "SELECT * FROM operation_authorizations WHERE authorization_id = ? FOR UPDATE";
        List<OperationAuthorization> list = jdbcTemplate.query(sql, new OperationAuthorizationRowMapper(), authorizationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<TransactionApproval> findApprovalById(String approvalId) {
        List<TransactionApproval> approvals = jdbcTemplate.query(
            "SELECT * FROM transaction_approvals WHERE approval_id = ?",
            (rs, rowNum) -> new TransactionApproval(
                rs.getString("approval_id"), rs.getString("authorization_id"),
                new SubjectId(rs.getString("subject_id")), new IntentDigest(rs.getString("intent_digest")),
                ApprovalDecision.valueOf(rs.getString("decision")), rs.getTimestamp("decided_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
                new DelegationAttribution(new SubjectId(rs.getString("subject_id")),
                    rs.getString("actor_id") == null ? null : new ActorId(rs.getString("actor_id"))),
                rs.getString("approver_id") == null ? null : new SubjectId(rs.getString("approver_id")),
                rs.getBoolean("human_confirmed")
            ), approvalId);
        return approvals.stream().findFirst();
    }

    @Override
    public boolean consumeApprovalAtomic(TransactionApproval approval, Instant now) {
        Objects.requireNonNull(approval, "approval cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        // Executa atualização atômica: consome apenas se não estiver consumida e se não estiver expirada
        String sql = """
            UPDATE transaction_approvals
            SET consumed_at = ?
            WHERE approval_id = ?
              AND consumed_at IS NULL
              AND expires_at >= ?
              AND authorization_id = ? AND subject_id = ? AND intent_digest = ?
              AND decision = 'APPROVED'
              AND actor_id IS NOT DISTINCT FROM ?
              AND approver_id IS NOT DISTINCT FROM ?
              AND human_confirmed = ?
              AND decided_at = ? AND expires_at = ?
            """;

        int rows = jdbcTemplate.update(sql, Timestamp.from(now), approval.approvalId(), Timestamp.from(now),
            approval.authorizationId(), approval.subjectId().value(), approval.intentDigest().value(),
            approval.delegationAttribution().actorId() == null ? null : approval.delegationAttribution().actorId().value(),
            approval.approverId() == null ? null : approval.approverId().value(), approval.humanConfirmed(),
            Timestamp.from(approval.decidedAt()), Timestamp.from(approval.expiresAt()));
        return rows == 1;
    }

    private static class OperationAuthorizationRowMapper implements RowMapper<OperationAuthorization> {
        @Override
        public OperationAuthorization mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new OperationAuthorization(
                rs.getString("authorization_id"),
                new OperationId(rs.getString("operation_id")),
                new OperationType(rs.getString("operation_type")),
                new SubjectId(rs.getString("subject_id")),
                rs.getString("actor_id") != null ? new ActorId(rs.getString("actor_id")) : null,
                new IntentVersion(rs.getLong("intent_version")),
                new IntentDigest(rs.getString("intent_digest")),
                rs.getString("policy_version"),
                AuthorizationState.valueOf(rs.getString("state")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                null,
                null,
                null
            );
        }
    }
}

