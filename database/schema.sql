CREATE TABLE animals (
                         id UUID PRIMARY KEY,
                         ear_tag VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE trays (
                       id UUID PRIMARY KEY,
                       rfid VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE animal_parts (
                              id UUID PRIMARY KEY,
                              animal_id UUID NOT NULL REFERENCES animals(id)
);

CREATE TABLE tray_parts (
                            tray_id UUID NOT NULL REFERENCES trays(id),
                            animal_part_id UUID NOT NULL REFERENCES animal_parts(id),
                            PRIMARY KEY (tray_id, animal_part_id),
                            UNIQUE (animal_part_id)
);

CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          product_type VARCHAR(100) NOT NULL
);

CREATE TABLE product_trays (
                               product_id UUID NOT NULL REFERENCES products(id),
                               tray_id UUID NOT NULL REFERENCES trays(id),
                               PRIMARY KEY (product_id, tray_id)
);