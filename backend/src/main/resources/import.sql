-- Delete timeseries table if exists
DROP TABLE IF EXISTS timeseries CASCADE;

-- Delete timeseries_payload table if exists
DROP TABLE IF EXISTS timeseries_payload CASCADE;

-- Create timeseries table
CREATE TABLE timeseries
(
    id          serial PRIMARY KEY,
    container_id BIGINT NOT NULL,
    measurement TEXT NOT NULL,
    field       TEXT NOT NULL,
    symbolic_name TEXT NULL,
    device      TEXT NULL,
    location    TEXT NULL
);

-- Create timeseries_payload table
CREATE TABLE timeseries_payload
(
    id            BIGSERIAL,
    timeseries_id INTEGER REFERENCES timeseries (id) ON DELETE CASCADE,
    time          BIGINT NOT NULL,
    double_value         DOUBLE PRECISION NULL,
    int_value         INTEGER NULL,
    string_value         TEXT NULL,
    boolean_value         boolean NULL
);

-- Create hypertable for timeseries_payload on time
SELECT create_hypertable('timeseries_payload', by_range('time', 864 * 1e11)); -- Chunk size of one day in nanoseconds

-- Insert initial data into timeseries table
INSERT INTO timeseries ( measurement, device, location, field, container_id )
VALUES ('Measurement 1', 'Robot 1', 'Factory Hall 1', 'value', 11),
       ('Measurement 1', 'Robot 2', 'Factory Hall 1', 'value', 11),
       ('Measurement 2', 'Robot 2', 'Factory Hall 1', 'value', 11);

-- Insert initial data into timeseries_payload table with random values
INSERT INTO timeseries_payload (time, timeseries_id, double_value)
SELECT (random() * 1e18),
       1,
       random() * 100
FROM generate_series(1, 100);
