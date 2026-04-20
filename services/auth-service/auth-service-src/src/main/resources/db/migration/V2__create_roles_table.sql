CREATE TABLE roles
(
    id      BIGSERIAL    NOT NULL,
    user_id UUID         NOT NULL,
    role    VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT fk_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_roles_user_role UNIQUE (user_id, role)
);

CREATE INDEX idx_roles_user_id ON roles (user_id);