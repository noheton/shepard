-- DB Version: 16
-- OS Type: linux
-- DB Type: mixed
-- Total Memory (RAM): 16 GB
-- CPUs num: 8
-- Connections num: 20
-- Data Storage: ssd

ALTER SYSTEM SET max_connections = '20';
ALTER SYSTEM SET shared_buffers = '4GB';
ALTER SYSTEM SET effective_cache_size = '12GB';
ALTER SYSTEM SET maintenance_work_mem = '1GB';
-- Effects accuracy of query planner - higher value = more detailed statistics, therefore better queries, but higher resources cons.
ALTER SYSTEM SET default_statistics_target = '300';
ALTER SYSTEM SET random_page_cost = '1.1';
-- Only useful for concurrent IO loads like in SSDs
ALTER SYSTEM SET effective_io_concurrency = '200';
ALTER SYSTEM SET work_mem = '26214kB';
ALTER SYSTEM SET huge_pages = 'off';
ALTER SYSTEM SET max_worker_processes = '8';
ALTER SYSTEM SET max_parallel_workers_per_gather = '4';
ALTER SYSTEM SET max_parallel_workers = '8';
ALTER SYSTEM SET max_parallel_maintenance_workers = '4';

-- WAL setup
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET min_wal_size = '1GB';
ALTER SYSTEM SET max_wal_size = '4GB';
ALTER SYSTEM SET checkpoint_completion_target = '0.9';
ALTER SYSTEM SET checkpoint_timeout = '10min';

-- Autovacuum & Analyze setup
ALTER SYSTEM SET autovacuum_naptime = '60s';
ALTER SYSTEM SET autovacuum_vacuum_threshold = '1000';
ALTER SYSTEM SET autovacuum_analyze_threshold = '1000';
ALTER SYSTEM SET autovacuum_vacuum_scale_factor = '0.0';
ALTER SYSTEM SET autovacuum_analyze_scale_factor = '0.0';
ALTER SYSTEM SET autovacuum_vacuum_cost_limit = '250';