CREATE TABLE admin_audit_log (
    id          UUID                     PRIMARY KEY,
    admin_id    UUID                     NOT NULL,
    action      VARCHAR(50)              NOT NULL,
    target_type VARCHAR(30)              NOT NULL,
    target_id   VARCHAR(64),
    reason      VARCHAR(500),
    metadata    JSONB,
    ip          VARCHAR(64),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_log_admin_id   ON admin_audit_log (admin_id, created_at DESC);
CREATE INDEX idx_admin_audit_log_target     ON admin_audit_log (target_type, target_id);
