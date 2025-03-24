-- DB Version: 16
-- OS Type: linux
-- DB Type: mixed
-- Total Memory (RAM): 16 GB
-- CPUs num: 8
-- Connections num: 20
-- Data Storage: ssd

-- Maximum number of concurrent database connections
ALTER SYSTEM SET max_connections = '20';
-- Memory allocated for shared buffers (caching data pages)
ALTER SYSTEM SET shared_buffers = '4GB';
-- Estimated cache size available for PostgreSQL, helps query planner
ALTER SYSTEM SET effective_cache_size = '12GB';
-- Memory for maintenance operations like VACUUM, CREATE INDEX, etc.
ALTER SYSTEM SET maintenance_work_mem = '1GB';
-- Effects accuracy of query planner - higher value = more detailed statistics, therefore better queries, but higher resources cons.
ALTER SYSTEM SET default_statistics_target = '300';
-- Cost of fetching random disk pages; lower values benefit SSDs
ALTER SYSTEM SET random_page_cost = '1.1';
-- Only useful for concurrent IO loads like in SSDs
ALTER SYSTEM SET effective_io_concurrency = '200';
-- Memory allocated per query operation (sorting, joins, etc.)
ALTER SYSTEM SET work_mem = '26214kB';
-- Controls usage of Huge Pages; set to 'on' for large memory systems
ALTER SYSTEM SET huge_pages = 'off';
-- Number of background worker processes for parallel execution
ALTER SYSTEM SET max_worker_processes = '8';
-- Maximum parallel workers per query execution (per Gather node)
ALTER SYSTEM SET max_parallel_workers_per_gather = '4';
-- Total number of parallel workers available to the system
ALTER SYSTEM SET max_parallel_workers = '8';
-- Maximum parallel workers for maintenance tasks (e.g., VACUUM, CREATE INDEX)
ALTER SYSTEM SET max_parallel_maintenance_workers = '4';

-- WAL setup
-- Memory allocated for WAL (write-ahead log) buffers
ALTER SYSTEM SET wal_buffers = '16MB';
-- Minimum size of WAL before triggering a checkpoint
ALTER SYSTEM SET min_wal_size = '1GB';
-- Maximum WAL size before forcing a checkpoint
ALTER SYSTEM SET max_wal_size = '4GB';
-- Target duration for checkpoints; higher values reduce I/O spikes
ALTER SYSTEM SET checkpoint_completion_target = '0.9';
-- Maximum time between automatic checkpoints
ALTER SYSTEM SET checkpoint_timeout = '10min';

-- Autovacuum & Analyze setup
-- Time between autovacuum runs
ALTER SYSTEM SET autovacuum_naptime = '60s';
-- Minimum number of row updates/deletes before vacuum triggers
ALTER SYSTEM SET autovacuum_vacuum_threshold = '1000';
-- Minimum number of row inserts/updates before analyze triggers
ALTER SYSTEM SET autovacuum_analyze_threshold = '1000';
-- Fraction of table size to trigger vacuum (0.0 means threshold only)
ALTER SYSTEM SET autovacuum_vacuum_scale_factor = '0.0';
-- Fraction of table size to trigger analyze (0.0 means threshold only)
ALTER SYSTEM SET autovacuum_analyze_scale_factor = '0.0';
-- Cost-based limit for vacuum operations to prevent excessive I/O
ALTER SYSTEM SET autovacuum_vacuum_cost_limit = '250';
