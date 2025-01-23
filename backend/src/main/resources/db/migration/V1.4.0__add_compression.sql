-- drop existing index because with compression enabled it is useless
DROP INDEX "timeseries_id_index";

-- drop primary key column of timeseries_data_points because it is useless
ALTER TABLE timeseries_data_points DROP COLUMN "id";

-- enable compression on hypertable to reduce disk space
ALTER TABLE timeseries_data_points SET (
  timescaledb.compress,
  timescaledb.compress_segmentby = 'timeseries_id',
  timescaledb.compress_orderby='time'
);

-- timeseries data will be compressed automatically if it is older than one day
-- this is done by a db job that runs every 12 hours per default
SELECT add_compression_policy('timeseries_data_points', BIGINT '86400000000000');

