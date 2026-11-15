CREATE TABLE IF NOT EXISTS trays (
    id UUID PRIMARY KEY,
    rfid VARCHAR(100) NOT NULL UNIQUE,
    max_weight NUMERIC NOT NULL CHECK (max_weight > 0),
    part_type VARCHAR(100)
);
CREATE TABLE IF NOT EXISTS animal_parts (
    id UUID PRIMARY KEY,
    animal_id UUID NOT NULL,
    part_type VARCHAR(100) NOT NULL,
    weight NUMERIC NOT NULL CHECK (weight > 0),
    tray_id UUID NOT NULL REFERENCES trays(id)
);
CREATE INDEX IF NOT EXISTS animal_parts_tray_id_idx ON animal_parts(tray_id);
