-- target_id is not a foreign key: it points at a post, a comment or a user depending on target_type, and a
-- report has to outlive a hard deleted post
CREATE TABLE reports (
    id                UUID                     PRIMARY KEY,
    reporter_id       UUID                     NOT NULL REFERENCES users (id),
    target_type       VARCHAR(20)              NOT NULL,
    target_id         UUID                     NOT NULL,
    reason            VARCHAR(30)              NOT NULL,
    description       VARCHAR(500),
    status            VARCHAR(20)              NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    resolved_by       UUID,
    resolved_at       TIMESTAMP(6) WITH TIME ZONE,
    resolution_action VARCHAR(20),
    resolution_note   VARCHAR(500),
    -- one report per user per target, for good: a closed report cannot be sent again to nag the admins
    CONSTRAINT uk_reports_reporter_target UNIQUE (reporter_id, target_type, target_id)
);

CREATE INDEX idx_reports_status_created ON reports (status, created_at DESC);
CREATE INDEX idx_reports_target         ON reports (target_type, target_id);
