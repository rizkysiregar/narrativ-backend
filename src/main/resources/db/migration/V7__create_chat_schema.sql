-- =============================================================
-- V2__create_chat_schema.sql
-- Chat feature: conversations, participants, messages, attachments
-- =============================================================

-- ------------------------------------------------------------
-- ENUM types
-- ------------------------------------------------------------
CREATE TYPE conversation_type AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE message_status AS ENUM ('SENT', 'DELIVERED', 'READ');
CREATE TYPE message_type AS ENUM ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'SYSTEM');

-- ------------------------------------------------------------
-- conversations
-- Represents a chat thread (DM or group)
-- ------------------------------------------------------------
CREATE TABLE conversations
(
    id         UUID PRIMARY KEY           DEFAULT gen_random_uuid(),
    type       conversation_type NOT NULL DEFAULT 'DIRECT',
    name       VARCHAR(100),                             -- NULL for DIRECT, required for GROUP
    avatar_url TEXT,
    created_by BIGINT            NOT NULL,               -- FK to your existing users table
    created_at TIMESTAMPTZ       NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ       NOT NULL DEFAULT now(), -- bumped on new message
    deleted_at TIMESTAMPTZ                               -- soft delete for groups

    -- If you have a users table:
    -- CONSTRAINT fk_conv_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Fast lookup: "all conversations ordered by latest activity"
CREATE INDEX idx_conversations_updated_at ON conversations (updated_at DESC);
CREATE INDEX idx_conversations_created_by ON conversations (created_by);

-- ------------------------------------------------------------
-- conversation_participants
-- Many-to-many: users <-> conversations
-- ------------------------------------------------------------
CREATE TABLE conversation_participants
(
    id                   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    conversation_id      UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id              BIGINT      NOT NULL,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at              TIMESTAMPTZ, -- NULL = still in conversation
    is_admin             BOOLEAN     NOT NULL DEFAULT false,
    -- last message the user has seen (for unread count)
    last_read_message_id UUID,

    CONSTRAINT uq_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_cp_user_id ON conversation_participants (user_id);
CREATE INDEX idx_cp_conversation_id ON conversation_participants (conversation_id);

-- ------------------------------------------------------------
-- messages
-- Core message table
-- ------------------------------------------------------------
CREATE TABLE messages
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    conversation_id UUID           NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       BIGINT         NOT NULL,
    type            message_type   NOT NULL DEFAULT 'TEXT',
    content         TEXT, -- NULL for pure attachment messages
    status          message_status NOT NULL DEFAULT 'SENT',
    reply_to_id     UUID           REFERENCES messages (id) ON DELETE SET NULL,
    is_deleted      BOOLEAN        NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()

    -- CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

-- Most common query: "give me messages in conversation X, newest first"
CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);

CREATE INDEX idx_messages_sender_id
    ON messages (sender_id);

-- Partial index: only index non-deleted messages (saves space)
CREATE INDEX idx_messages_active
    ON messages (conversation_id, created_at DESC) WHERE is_deleted = false;

-- ------------------------------------------------------------
-- message_attachments
-- One message can have multiple files
-- ------------------------------------------------------------
CREATE TABLE message_attachments
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    message_id    UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    url           TEXT        NOT NULL,
    thumbnail_url TEXT,
    file_name     VARCHAR(255),
    mime_type     VARCHAR(100),
    file_size     BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_message_id ON message_attachments (message_id);

-- ------------------------------------------------------------
-- message_reactions  (bonus — easy to add now)
-- ------------------------------------------------------------
CREATE TABLE message_reactions
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    message_id UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reaction UNIQUE (message_id, user_id, emoji)
);

CREATE INDEX idx_reactions_message_id ON message_reactions (message_id);

-- ------------------------------------------------------------
-- Auto-update conversations.updated_at when a message is inserted
-- ------------------------------------------------------------
CREATE
OR REPLACE FUNCTION fn_bump_conversation_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
UPDATE conversations
SET updated_at = now()
WHERE id = NEW.conversation_id;
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_bump_conversation_updated_at
    AFTER INSERT
    ON messages
    FOR EACH ROW
    EXECUTE FUNCTION fn_bump_conversation_updated_at();
