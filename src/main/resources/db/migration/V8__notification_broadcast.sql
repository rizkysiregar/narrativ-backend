-- history of the push notifications an admin sent to a topic, including the ones Firebase refused
CREATE TABLE notification_broadcast (
    id           UUID                        PRIMARY KEY,
    admin_id     UUID                        NOT NULL,
    topic        VARCHAR(100)                NOT NULL,
    title        VARCHAR(100)                NOT NULL,
    body         VARCHAR(500)                NOT NULL,
    status       VARCHAR(10)                 NOT NULL,
    fcm_response TEXT,
    error        VARCHAR(500),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_broadcast_created_at ON notification_broadcast (created_at DESC);
