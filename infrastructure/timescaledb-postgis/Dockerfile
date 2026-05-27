# SPATIAL-V6-001 — Decision D2: retire separate postgis container
# by co-locating the PostGIS extension on the existing TimescaleDB instance.
#
# The timescale/timescaledb:2.24.0-pg16 image is Alpine-based.
# PostGIS 3.5.x is available in the Alpine community repository as
# the `postgis` package (verified: `apk search postgis` returns postgis-3.5.2-r0).
#
# Build:
#   docker build -t shepard-timescaledb-postgis:2.24.0-pg16 infrastructure/timescaledb-postgis/
#
# Or via docker compose (see docker-compose.yml build: stanza for timescaledb service).
#
# Operator note: after the first start with this image, connect to the database
# and run `CREATE EXTENSION IF NOT EXISTS postgis;` once.  The Flyway migration
# V2.0.0__green_field_schema.sql does this automatically when the spatiotemporal
# plugin runs its migration step.
#
# Reference: aidocs/data/90 §10 Decision D2; plugins/spatiotemporal/docs/install.md

FROM timescale/timescaledb:2.24.0-pg16

# Add PostGIS to the Alpine environment.
# postgis package includes: libgeos, libproj, libgdal, postgis extension.
RUN apk add --no-cache postgis
