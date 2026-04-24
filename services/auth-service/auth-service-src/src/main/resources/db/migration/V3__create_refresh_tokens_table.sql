CREATE TABLE refresh_tokens
(
    id         UUID        DEFAULT gen_random_uuid() NOT NULL,
    user_id    UUID                                  NOT NULL,
    token_hash VARCHAR(64)                           NOT NULL,
    expires_at TIMESTAMPTZ                           NOT NULL,
    revoked    BOOLEAN     DEFAULT FALSE             NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()             NOT NULL,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);