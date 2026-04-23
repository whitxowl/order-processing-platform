CREATE TABLE reservations
(
    id          BIGSERIAL    NOT NULL,
    order_id    VARCHAR(36)  NOT NULL,
    product_id  VARCHAR(36)  NOT NULL,
    quantity    INTEGER      NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_reservations          PRIMARY KEY (id),
    CONSTRAINT uq_reservations_order_id UNIQUE (order_id),
    CONSTRAINT chk_reservations_qty     CHECK (quantity > 0),
    CONSTRAINT chk_reservations_status  CHECK (status IN ('NEW', 'RESERVED', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_order_id   ON reservations (order_id);
CREATE INDEX idx_reservations_product_id ON reservations (product_id);
CREATE INDEX idx_reservations_status     ON reservations (status);