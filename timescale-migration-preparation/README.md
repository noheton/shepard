# timescale-migration-preparation

This folder provides a set of python scripts that check whether an existing InfluxDB instance contains data points
which cannot be migrated (for example a symbolic name containing a space or dot).

For administrators, a Docker container is provided which allows for a fast checking whether the own instance is affected,
and if so, a way to fix the issues by renaming the affected data points.

**Warning**: Fixing/renaming can take considerable time, because all affected timeseries points must be read from
InfluxDB and written with a new tag again. Shepard cannot be available during that time.

## Usage

1. The `docker-compose.yml` file in the `infrastructure` folder contains an entry `timescale-migration-preparation`.
   It is in a separate profile `timeseries-migration` and thus will only be executed by manual interaction.

2. Stop all shepard services (`docker compose down`).

3. run `docker compose --profile timeseries-migration run timescale-migration-preparation` to start the migration container. This will start the migration container, as well as all necessary dependencies (InfluxDB, Neo4j) and open a bash-shell within the container.

4. Wait for all containers having started (around 10s).

5. Within the container, run `python check.py` to analyze the database.
6. If necessary, run `python fix.py` to fix any problems (can take considerable time).
7. To exit the container, type `exit`.
8. Stop all other containers using `docker compose down`
9. Continue with shepard TimescaleDB migration

## Hints:

- wait for all services to have started completely
- running the script (depending on data to be processed) can run for several hours
- a logfile will be written to `./log/log.log` to check progress (warning very verbose)
  ```
  ...
  2025-02-11 11:29:31.403 | INFO     | fix_neo4j_consistency:fix_neo4j:26 - MATCH (ts_c:TimeseriesContainer)-[*..2]-(n:Timeseries) WHERE ts_c.database = "myInvalidData" AND n.measurement="cpu_load_short_2" and n.symbolicName ="invalid.my.variable\ " SET n.symbolicName = "invalid_my_variable_____" return DISTINCT n
  2025-02-11 11:29:31.443 | INFO     | fix_neo4j_consistency:fix_neo4j:29 - EagerResult(records=[], summary=<neo4j._work.summary.ResultSummary object at 0x7f15cc4e3a90>, keys=['n'])
  2025-02-11 11:29:31.443 | SUCCESS  | __main__:main:25 - Migration preperation performed successfully
  ```
- in case replacements will be performed the information about them will be also stored in the `./log/` folder as a seperate json file
  ```
  [
      {
          "database": "myInvalidData",
          "measurement": "cpu_load_short",
          "key": "symbolic_name",
          "old_tag": "invalid / symobolic name ",
          "new_tag": "invalid___symobolic_name______"
      },
      {
          "database": "myInvalidData",
          "measurement": "cpu_load_short_2",
          "key": "symbolic_name",
          "old_tag": "invalid.my.variable ",
          "new_tag": "invalid_my_variable_____"
      }
  ]
  ```
