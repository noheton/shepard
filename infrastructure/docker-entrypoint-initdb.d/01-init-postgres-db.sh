set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

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

	CREATE TABLE timeseries_payload
	(
		id            BIGSERIAL,
		timeseries_id INTEGER REFERENCES timeseries (id) ON DELETE CASCADE,
		time          TIMESTAMPTZ NOT NULL,
		double_value         DOUBLE PRECISION NULL,
		int_value         INTEGER NULL,
		string_value         TEXT NULL,
		boolean_value         boolean NULL
	);

	SELECT create_hypertable('timeseries_payload', 'time');

	CREATE USER $POSTGRES_SHEPARD_USER WITH PASSWORD '$POSTGRES_SHEPARD_USER_PW';
	GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $POSTGRES_SHEPARD_USER;
	ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $POSTGRES_SHEPARD_USER;
EOSQL