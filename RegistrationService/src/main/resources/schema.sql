CREATE TABLE IF NOT EXISTS animals
(
    id
    UUID
    PRIMARY
    KEY,
    ear_tag
    VARCHAR
(
    50
) NOT NULL UNIQUE,
    arrival_date DATE NOT NULL,
    weight DOUBLE PRECISION NOT NULL CHECK
(
    weight >
    0
),
    origin VARCHAR
(
    255
) NOT NULL
    );
CREATE TABLE IF NOT EXISTS event_outbox (
    id UUID PRIMARY KEY, event_type VARCHAR(40) NOT NULL, payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
