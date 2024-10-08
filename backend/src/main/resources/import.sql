-- Delete timeseries table if exists
DROP TABLE IF EXISTS timeseries CASCADE;

-- Delete timeseries_payload table if exists
DROP TABLE IF EXISTS timeseries_payload CASCADE;

-- Create timeseries table
CREATE TABLE timeseries
(
    id          SERIAL PRIMARY KEY,
    measurement TEXT NOT NULL,
    device      TEXT NULL,
    location    TEXT NULL,
    symbolic_name TEXT NULL,
    field       TEXT NULL
);

-- Create timeseries_payload table
CREATE TABLE timeseries_payload
(
    id            SERIAL,
    timeseries_id INTEGER REFERENCES timeseries (id),
    time          TIMESTAMPTZ NOT NULL,
    value         DOUBLE PRECISION
);

-- Create hypertable for timeseries_payload on time
SELECT create_hypertable('timeseries_payload', 'time');

-- Insert initial data into timeseries table
INSERT INTO timeseries (measurement, device, location)
VALUES ('Measurement 1', 'Robot 1', 'Factory Hall 1'),
       ('Measurement 1', 'Robot 2', 'Factory Hall 1'),
       ('Measurement 2', 'Robot 2', 'Factory Hall 1');

-- Insert initial data into timeseries_payload table with random values
INSERT INTO timeseries_payload (time, timeseries_id, value)
SELECT generate_series(now() - INTERVAL '24 hours', now(), INTERVAL '5 minutes'),
       floor(random() * 3 + 1)::INTEGER,
       random() * 100
FROM generate_series(1, 100);
