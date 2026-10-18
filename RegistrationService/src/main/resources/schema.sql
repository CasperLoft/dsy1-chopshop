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