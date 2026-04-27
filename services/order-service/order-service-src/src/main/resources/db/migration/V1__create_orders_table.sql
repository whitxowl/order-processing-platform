CREATE TABLE orders
(
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_orders         PRIMARY KEY (id),
    CONSTRAINT chk_orders_qty    CHECK (quantity > 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status  ON orders (status);