CREATE TABLE users
(
    id             UUID         DEFAULT gen_random_uuid() NOT NULL,
    email          VARCHAR(255)                           NOT NULL,
    password_hash  VARCHAR(255)                           NOT NULL,
    email_verified BOOLEAN      DEFAULT FALSE             NOT NULL,
    enabled        BOOLEAN      DEFAULT TRUE              NOT NULL,
    created_at     TIMESTAMPTZ  DEFAULT now()             NOT NULL,
    updated_at     TIMESTAMPTZ  DEFAULT now()             NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);