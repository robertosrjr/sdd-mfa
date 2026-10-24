-- Written only by the trusted approval interaction, never from execution request claims.
-- Legacy approvals lack human evidence and must be re-approved by the trusted interaction.
ALTER TABLE transaction_approvals ADD COLUMN actor_id VARCHAR(128);
ALTER TABLE transaction_approvals ADD COLUMN approver_id VARCHAR(128);
ALTER TABLE transaction_approvals ADD COLUMN human_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
