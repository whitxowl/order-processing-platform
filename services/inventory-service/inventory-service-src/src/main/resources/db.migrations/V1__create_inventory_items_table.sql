CREATE TABLE inventory_items
(
    id         BIGSERIAL    NOT NULL,
    product_id VARCHAR(36)  NOT NULL,
    quantity   INTEGER      NOT NULL DEFAULT 0,
    reserved   INTEGER      NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_items PRIMARY KEY (id),
    CONSTRAINT uq_inventory_items_product_id UNIQUE (product_id),
    CONSTRAINT chk_inventory_items_quantity  CHECK (quantity >= 0),
    CONSTRAINT chk_inventory_items_reserved  CHECK (reserved >= 0),
    CONSTRAINT chk_inventory_items_available CHECK (quantity >= reserved)
);

CREATE INDEX idx_inventory_items_product_id ON inventory_items (product_id);