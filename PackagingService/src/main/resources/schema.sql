CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    product_type VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS product_trays (
    product_id UUID NOT NULL REFERENCES products(id),
    tray_rfid VARCHAR(100) NOT NULL,
    PRIMARY KEY (product_id, tray_rfid)
);
