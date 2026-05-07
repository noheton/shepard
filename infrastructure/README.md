# shepard deployment

This folder contains everything you need to set up a shepard instance with Docker and Docker Compose. For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/shepard/-/wikis/home).

[[_TOC_]]

## Prerequisites

> A minimal configuration without publicly trusted SSL certificates and DNS is possible. In this case, you do not need to install and configure a reverse proxy. Instead, you should change the `ports` inside `docker-compose.yml` so that the containers are externally accessible. This way you cannot use subdomains, instead you can directly address the respective containers via ip and port. Also, all communication between clients and your instance is not encrypted, which is a security risk in itself.

- [Docker](https://docs.docker.com/engine/) and [Docker Compose](https://docs.docker.com/compose/) are installed.
- There is an OpenID Connect identity provider that uses [JSON web tokens](https://jwt.io/) as access tokens. [Keycloak](https://www.keycloak.org/) is recommended, but not required.
- You have SSL certificates and DNS entries (both with and without wildcard respectively) for your host.

## System requirements

> Depending on how you plan to use shepard, the system requirements can vary greatly. While most services are relatively lightweight, the databases and shepard backend can be quite demanding. As a starting point, 8 GB of memory may be sufficient. Also, most services benefit greatly from many CPU cores, so there should be at least 4 cores/8 threads. The amount of disk space you need depends directly on the size of the data you want to manage with shepard.

- [Neo4j system requirements](https://neo4j.com/docs/operations-manual/current/installation/requirements/#deployment-requirements-hardware)
- [MongoDB system requirements](https://www.mongodb.com/blog/post/performance-best-practices-hardware-and-os-configuration)

## Installation

> These installation instructions result in a complete and opinionated environment with subdomains and SSL. The databases are configured to be directly accessible over the network for debugging purposes. A customized installation with a different configuration is easily possible and should be considered.

### 1. Clone repository

```bash
git clone https://gitlab.com/dlr-shepard/shepard.git
cd shepard/infrastructure
```

### 2. Prepare the reverse proxy

- Apply for SSL certificates and store them in the system under `proxy/ssl/shepard.crt` (this needs to be a fullchain certificate) and `proxy/ssl/shepard.key`
- Ideally, the wildcard subdomain (`*`) is included directly in the main certificate as a Subject Alternative Name (SAN) to only have to care about one certificate
- Make sure that the DNS records resolve both the main name (e.g. `example.com`) and the wildcard subdomain (e.g. `test.example.com`)

### 3. Set up the reverse proxy

- Configure the [Caddyfile](proxy/Caddyfile) as needed:

```bash
# IMPORTANT: replace HOSTNAME with your real hostname
sed -i "s@hostname_placeholder_do_not_change@HOSTNAME@" proxy/Caddyfile
```

- Replace the hostname placeholder in [index.html](proxy/shepard/index.html):

```bash
# IMPORTANT: replace HOSTNAME with your real hostname
sed -i "s@hostname_placeholder_do_not_change@HOSTNAME@" proxy/shepard/index.html
```

### 4. Create necessary directories

- Prepare needed directories for volume mounts:

```bash
mkdir /opt/shepard/backend/logs/ /opt/shepard/backend/config /opt/shepard/timescaledb/
```

- Adapt user permissions

```bash
sudo chown 185:185 /opt/shepard/backend/logs/ /opt/shepard/backend/config
sudo chown 1000:1000 /opt/shepard/timescaledb/
```

### 5. Check configuration in `docker-compose.yml` and especially check available memory

- Backend:

```yaml
JAVA_OPTS: "-Xms2G -Xmx2G"
```

- Neo4j:

```yaml
NEO4J_dbms_memory_heap_initial__size: 2G
NEO4J_dbms_memory_heap_max__size: 2G
NEO4J_dbms_memory_pagecache_size: 3G
```

- MongoDB:

```yaml
command: --wiredTigerCacheSizeGB 2.0
```

### 6. Copy the file `.env.example` to `.env`

```bash
# copy configuration file
cp .env.example .env
```

### 7. Set passwords and configuration in the `.env` file

- All variables except `OIDC_ROLE` must be set!
- **URLs have to end with a trailing slash**
- The database passwords can be changed arbitrarily at the beginning
- TimescaleDB and PostGIS are extensions of PostgreSQL.
  Theoretically they can be stored in the same database but it is recommended to use separate instances.

| Variable                     | Description                                                                                               | Example                                                                                           |
| ---------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| BACKEND_URL                  | contains the URL of the backend to be accessed by the clients                                             | `https://backend.shepard.example.com/`                                                            |
| NEO4J_PW                     | initial Neo4j password                                                                                    |                                                                                                   |
| MONGO_ROOT_USERNAME          | MongoDB admin name (automatically created on a fresh instance)                                            |                                                                                                   |
| MONGO_ROOT_PASSWORD          | MongoDB admin password (automatically created on a fresh instance)                                        |                                                                                                   |
| MONGO_DATABASE               | MongoDB database name for shepard (automatically created on a fresh instance)                             |                                                                                                   |
| MONGO_USERNAME               | MongoDB non-admin user username for `MONGO_DATABASE` database                                             |                                                                                                   |
| MONGO_PASSWORD               | MongoDB non-admin user password for `MONGO_DATABASE` database                                             |                                                                                                   |
| POSTGRES_DB                  | Database name                                                                                             | postgres                                                                                          |
| POSTGRES_USER                | Username for the postgres admin account                                                                   | postgres                                                                                          |
| POSTGRES_PASSWORD            | Password for the postgres admin account                                                                   | password                                                                                          |
| POSTGRES_SHEPARD_USER        | Username for the shepard user account                                                                     | shepard                                                                                           |
| POSTGRES_SHEPARD_USER_PW     | Password for the shepard user account                                                                     | shepard_secret                                                                                    |
| OIDC_AUTHORITY               | is the URL of the oidc identity provider, which can be accessed by both the users and the shepard backend | `https://keycloak.example.com/realms/master/`                                                     |
| OIDC_PUBLIC                  | is the public key of the signature of the oidc identity provider (e.g. keycloak)                          | `MII...`                                                                                          |
| OIDC_ROLE                    | allows to restrict access to users with a specific realm role                                             | see [restrict access to users with specific roles](#restrict-access-to-users-with-specific-roles) |
| CLIENT_ID                    | is the client ID of the frontend as known to the oidc identity provider                                   | `example-client-id`                                                                               |
| FRONTEND_URL                 | URL of the frontend with trailing slash                                                                   | `https://frontend.shepard.example.com/`                                                           |
| FRONTEND_AUTH_SECRET         | A random secret string                                                                                    |
| SESSION_REFRESH_INTERVAL     | frontend session refresh interval in ms (defaults to 30secs)                                              | 30000                                                                                             |
| SHEPARD_SPATIAL_DATA_ENABLED | Enable experimental spatial data feature. Requires postgis                                                | `false`                                                                                           |
| COMPOSE_PROFILES             | Select the docker compose profiles that are active                                                        | Default: empty                                                                                    |

> **_NOTE:_** The `FRONTEND_AUTH_SECRET` could be any random generated string which will be used to hash JWT tokens. you can quickly create a good value on the command line using `openssl`
>
> ```bash
> $ openssl rand -base64 32
> ```

## Restrict access to users with specific roles

Some OpenID Connect identity providers such as [Keycloak](https://www.keycloak.org/) are able to add realm role information as part of access tokens. The access token then contains an additional claim `realm_access` like the following:

```json
{
  ...,
  "realm_access": {
    "roles": [
      "default-roles-master",
      "offline_access",
      "uma_authorization",
      "custom_role"
    ]
  },
  ...
}
```

The shepard backend can be configured to allow only users with a specific role. To do so, the optional `OIDC_ROLE` variable in `.env` can be set to the given role. From the next restart, users without this role will no longer be able to access shepard.

## Profiles in `docker-compose.yml`

Different (experimental) features can be activated using profiles.
The default profile contains all containers that are necessary for sheapard in default configuration.

Additionally, the following profiles are defined:

| Profile                         | Feature                                                                                                                                      |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| spatial                         | Enable postgis to enable experimental spatial data feature. The feature itself must be activated using the environment variables (see above) |
| monitoring                      | Enable the monitoring feature (see below).                                                                                                   |
| timescale-migration-preparation | Only used for the migration process, see migration documentation for more information                                                        |
| frontend-old                    | Enable old (legacy) frontend.                                                                                                                |

Profiles can either be selected on CLI, e.g.

`docker compose --profile spatial --profile monitoring up`

or by setting the `COMPOSE_PROFILES` environment variable (e.g. in `.env`).
Multiple profiles must be comma-separated.

## Start

Make sure that all requested resources are available. In particular, check the free memory, since the shepard backend and the databases will use a lot of it. You can adjust the maximum amount of used memory in `docker-compose.yml`.
If you are using experimental features, make sure that all profiles are selected accordingly.
Profiles must be set for every `docker compose` command, therefore it is recommended to use the `COMPOSE_PROFILES` environment variable.

```bash
docker compose pull
docker compose up -d
```

You can find the backend logs in `/opt/shepard/backend/logs`.

## How to use Metrics

Shepard backend provides a metrics endpoint.
You can access helpful information about the system and resource consumption by using this endpoint.
We recommend to use `Prometheus` as a monitoring system and data store and Grafana for visualization,
but you can access the metrics endpoint directly in the browser: `/shepard/doc/metrics/prometheus`.
You will receive a JSON document with the current values.

### Setup Prometheus

The `monitoring` profile contains all required containers for setting up Prometheus.

The configuration file for prometheus is located in `infrastructure/prometheus/prometheus.yml`.
In general the configuration should be correct.
You may have to adapt the configuration file if you changed you network name or any other configuration in the docker compose files.
The docker compose files that we provide make use of the same network called `shepard`.
Prometheus and shepard backend have to run in the same network, otherwise prometheus is not able to collect the metrics.
The configuration file contains one job that collects metrics from the metrics endpoint of shepard.
If the backend service is called `backend` the configuration should be correct.
Otherwise you have to adapt it.

### Setup Grafana

#### Adapt Caddyfile

Grafana is used for visualization.
In order to access Grafana, you have to configure it in the caddyfile.
Make a copy of an existing section and do the following changes:

```txt
https://grafana.shepard.example.com {
	reverse_proxy grafana:3000
  ...
}
```

Make sure to replace _shepard.example.com_ with the correct hostname of your system like for the other entries.

#### Configuration in .env file

You have to provide username and password for using Grafana.
They have to be configured in the .env file.

```txt
GRAFANA_ADMIN_USERNAME=grafana
GRAFANA_ADMIN_PASSWORD=secure_password
```

#### First steps with Grafana

Now you should be able to open the grafana ui via browser with the url you have configured in the caddyfile.
First, you need to configure a data source (prometheus).
Second, you can explore the metrics that are available.
Third, you can build a dashboard that provides a good overview over the system and resource consumption.

##### Adding a data source

Metrics are collected by and stored in prometheus.
In order to visualize them with Grafana, you have to add a connection to prometheus:

- open Grafana UI and login
- click on Connections/Add new connection
- search for Prometheus and select it
- use the following settings to connect:
  - Connection: http://prometheus:9090
- Click 'Save & test'

## Experimental features

There are profiles in `docker-compose.yml` that enable experimental extensions.

### Spatial Data

The experimental docker compose profile contains a docker container with PostGIS which is an extension for PostgreSQL.
In order to make use of this experimental feature, the administrator has to activate it via environment variables or configuration properties.

As environment variable:

```bash
SHEPARD_INFRASTRUCTURE_SPATIAL_ENABLED="true"
```

As configuration property:

```bash
shepard.infrastructure.spatial.enabled = true
```

The legacy keys `SHEPARD_SPATIAL_DATA_ENABLED` / `shepard.spatial-data.enabled`
are still honoured as deprecated aliases and will be removed in v6.0.

That enables the REST endpoints for spatial data.
Otherwise those endpoints return 404 and cannot be used.

### Use the experimental spatial profile

Set `COMPOSE_PROFILES` variable to `spatial`.

## Update

Always check [recently merged Merge Requests](https://gitlab.com/dlr-shepard/shepard/-/merge_requests?label_name%5B%5D=Breaking+Change&scope=all&sort=merged_at_desc&state=merged) with the `Breaking Change` label before updating the system, as some changes may require manual intervention.

Database upgrades may also require manual intervention. What exactly needs to be adjusted can be found in the respective changelogs. The shepard backend can handle a relatively wide range of database versions. Therefore, it is possible to sit out database upgrades for some time and still update shepard. This repository will always use a recent version of the respective databases that have been successfully tested to work with shepard.

The upgrade process consists of shutting down the docker containers, updating the git repository, and restarting the docker containers again.

```bash
# Go into infrastructure folder
cd infrastructure

# Stop Containers
docker compose down

# Check recent changes to the infrastructure folder
git fetch
git diff HEAD...origin/main .

# Stash local changes, pull, apply stash and perform adaptions if necessary
git stash
git pull
git stash apply

# Pull new versions of docker images and restart containers
docker compose pull
docker compose up -d
```

## Database Tweaking (Postgres, TimescaleDB, PostGIS)

When using Postgres or any of its variants like TimescaleDB or PostGIS, these databases come with pre-configured values for memory handling, query planning and internal database cleaning task.
These default values often work great for most use cases.
However, in the scope of the shepard project, large datasets needs to be handled by the Postgres-based databases.
Meaning that data insertion and data retrieval should be as fast as possible for gigabytes of data.

When working with the databases we found parameters that can influence the performance of the database.
The most important findings are documented below.

- There are lots of best-practice recommendations for general postgres database settings. These settings often depend on the hardware you are using and the resource you are willing to provide to the postgres database. This means that fine-tuning these parameters can be a time consuming task and optimal parameters depend on the used hardware. Therefore, we cannot provide a postgres configuration by default. But we recommend tools like https://pgtune.leopard.in.ua/ or https://postgresqlco.nf/tuning-guide, which provide a good starting point for tweaking a postgres database.
- When running the Postgres database in a docker container, the database only has access to 65MB of shared memory. This is often not enough for building or updating indexes. Therefore, we advice to set the `shm_size` parameter in the postgres docker container. This value should be at least the size of the `shared_buffers` postgres database setting.
- Postgres utilizes the `VACUUM` and `ANALYZE` commands as important clean-up tasks. They help to identify and remove dead tuples in the database and update information that are used by the query planner to efficiently plan and execute database queries. These commands need to be executed regularly either by the user, by a cron-job or by postgres itself. Postgres utilizes a feature called `autovacuum`, which automatically calls the `VACUUM` and `ANALYZE` commands depending on certain conditions. The most important condition is the number of changed rows in a database. Meaning that postgres runs the autovacuum scheduler every X seconds and checks if the number of changed rows is larger than a specific threshold. If this threshold is reached, the `VACUUM` or `ANALYZE` (or both) commands are run by postgres. In order to avoid calling the `VACUUM`/`ANALYZE` commands manually we recommend to adapt the default configuration for the autovacuum feature, so that it acts a little bit more aggressive and makes sure that the databases always has an up-to-date index and the latest information for the query planner.
  If the autovacuum feature actually executes depends on the number of changed rows. This threshold is calculated with the following formula:
  $`\text{threshold} = \text{autovacuum\_vacuum\_threshold} + \text{autovacuum\_vacuum\_scale\_factor} * \text{NUMBER\_OF\_ALL\_ROWS}`$. The following parameters are most important to fine-tune:
  - autovacuum_naptime - time between autovacuum checks
  - autovacuum_vacuum_threshold - absolute row threshold for vacuum
  - autovacuum_analyze_threshold - absolute row threshold for analyze
  - autovacuum_vacuum_scale_factor - relative row threshold for vacuum
  - autovacuum_analyze_scale_factor - relative row threshold for analyze
  - autovacuum_vacuum_cost_delay - delay in ms when autovacuuming and a specific cost threshold is exceeded
  - autovacuum_vacuum_cost_limit - the cost limit to trigger a cost delay

The database tweaking script (`tweak-db-settings.sql`) that we provide uses the following hardware specifications:

- 16GB RAM
- SSD Storage
- 8 virtual cores

This script or similar scripts generated by the recommended pgtune website can be applied to a running postgres container in the following way:
`cat tweak-db-settings.sql | docker exec -i DB_CONTAINER_NAME psql -U ADMIN_USERNAME`
This applies the database system settings to the whole database instance and therefore to ALL databases.
When the script successfully executed you need to restart the database or the container to bring make the changes into effect.

## Troubleshooting

### Check for Breaking Changes

Sometimes the installation does not work as expected or the system does not boot after an update. In these cases, you should check for `Breaking Changes` again, as you might have missed an important change.

### Review your configuration

Verify that the configuration meets the given requirements. The file must have the name `.env` and all variables from `.env.example` must be set. Also look at the provided URLs, as all URLs must end with a trailing slash.

### Read the logs

Most containers log to `STDOUT`. Therefore, you can observe the logs via `docker compose logs <containername>`.

The shepard backend also uses this method, but additionally writes to log files. These log files contain detailed log messages from the system and may contain important information about an issue. You can find the log files at `/opt/shepard/backend/tomcat/` unless you have changed the default location. The file `shepard.log` contains all logs since the last startup or rollover.

Some issues can be found in the frontend log. Open the web dev console (F12 in most browsers) and see whether there are errors in the log.

## Common issues

### Frontend stays blank or is not loading

You may have incorrectly entered one of the `sed ...` commands from [step 3](#3-set-up-the-reverse-proxy). Make sure that both the `Caddyfile` as well as the `index.html` file contain correct hostnames and subdomains.

### Frontend gets stuck on loading and the web dev console shows errors

You may have mistyped the backend URL in `.env`. Make sure that the backend URL is accessible by copying and pasting it into your browser.

### Frontend remains empty except for the navigation bar and the web dev console shows CORS errors

The frontend may have problems accessing the identity provider. Make sure that the `OIDC_AUTHORITY` variable is correct. For keycloak, the url looks like this: `https://my-keycloak.example.com/realms/master/`.

### Error while fetching collections: AuthenticationException: User info could not be retrieved

The backend may have issues accessing the identity provider. Ensure that the backend can access the identity provider using the URL in `OIDC_AUTHORITY`.

### Error while fetching collections: AuthenticationException: Invalid Authentication

The following error is reported in the backend log:

> Invalid token: JWT signature does not match locally computed signature. JWT validity cannot be asserted and should not be trusted.

You may have entered the `OIDC_PUBLIC` key incorrectly or used a wrong one. For Keycloak you can find the correct key under `Realm Settings > Keys > RS256 > Public Key`.

### Error while fetching collections: AuthenticationException: Invalid Authentication

The following error is reported in the backend log:

> User is missing required role: bt_shepard_users_test_msc

You may have entered the OIDC_ROLE variable incorrectly or made a mistake when configuring the identity provider. Ensure that the identity provider embeds the role information in the access tokens. For Keycloak, you must configure the realm roles to accomplish this.

### Permission issue for logs and API key config

When having the following error, it is a result of using two directories that require root permissions with a limited
access user (UID=185).

> LogManager error of type OPEN_FAILURE: Failed to set log file

To keep the backend docker image secure and clean we can keep relying on the same user and make sure to [set the right directory permissions](#4-create-necessary-directories).

### Permission issue for TimescaleDB

The following error indicates that the mounted volume requires different access permission to be able to store data in the hosting system.

> initdb: error: could not change permissions of directory "/var/lib/postgres/data": Operation not permitted

To solve it make sure to [set the right directory permissions](#4-create-necessary-directories).
