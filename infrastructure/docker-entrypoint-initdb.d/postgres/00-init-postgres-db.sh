set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

	-- Pre-create extensions the Flyway migrations need. These require
	-- superuser, which the unprivileged shepard app user (Flyway connects as
	-- it) does not have — so on a from-scratch cluster the migration
	-- V1.11.0__add_shepard_id_to_timeseries.sql would fail with
	-- "permission denied to create extension". Create them here as the
	-- bootstrap superuser so the migration's CREATE EXTENSION IF NOT EXISTS
	-- is a harmless no-op. (Hardening for full-instance reset — see
	-- docs/admin/runbooks/13-full-instance-reset.md.)
	CREATE EXTENSION IF NOT EXISTS pgcrypto;
	CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

	CREATE USER $POSTGRES_SHEPARD_USER WITH PASSWORD '$POSTGRES_SHEPARD_USER_PW';
  GRANT CREATE, USAGE ON SCHEMA public TO $POSTGRES_SHEPARD_USER;
	GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO $POSTGRES_SHEPARD_USER;
	ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $POSTGRES_SHEPARD_USER;

	-- PgBouncer auth_query support: SECURITY DEFINER function lets PgBouncer
	-- look up scram-sha-256 hashes without superuser access to pg_shadow.
	CREATE SCHEMA IF NOT EXISTS pgbouncer;
	GRANT USAGE ON SCHEMA pgbouncer TO $POSTGRES_SHEPARD_USER;
	CREATE OR REPLACE FUNCTION pgbouncer.get_auth(p_usename TEXT)
	  RETURNS TABLE(username TEXT, password TEXT) AS \$\$
	    SELECT usename::TEXT, passwd::TEXT FROM pg_shadow WHERE usename = p_usename;
	  \$\$ LANGUAGE sql SECURITY DEFINER;
	GRANT EXECUTE ON FUNCTION pgbouncer.get_auth(TEXT) TO $POSTGRES_SHEPARD_USER;
EOSQL
