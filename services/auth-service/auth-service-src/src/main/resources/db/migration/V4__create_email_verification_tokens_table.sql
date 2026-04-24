CREATE TABLE email_verification_tokens
(
    id           UUID        DEFAULT gen_random_uuid() NOT NULL,
    user_id      UUID                                  NOT NULL,
    token        VARCHAR(64)                           NOT NULL,
    expires_at   TIMESTAMPTZ                           NOT NULL,
    confirmed_at TIMESTAMPTZ                           NULL,
    created_at   TIMESTAMPTZ DEFAULT now()             NOT NULL,

    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_email_verification_tokens_token UNIQUE (token)
);