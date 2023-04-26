# shepard deployment

This repository contains everything you need to set up a shepard instance with Docker and Docker Compose. For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/documentation/-/wikis/home).

[[_TOC_]]

## Prerequisites

> A minimal configuration without publicly trusted SSL certificates and DNS is possible. In this case, you do not need to install and configure a reverse proxy. Instead, you should change the `ports` inside `docker-compose.yml` so that the containers are externally accessible. This way you cannot use subdomains, instead you can directly address the respective containers via ip and port. Also, all communication between clients and your instance is not encrypted, which is a security risk in itself.

- [Docker](https://docs.docker.com/engine/) and [Docker Compose](https://docs.docker.com/compose/) are installed.
- There is an OpenID Connect identity provider that uses [JSON web tokens](https://jwt.io/) as access tokens. [Keycloak](https://www.keycloak.org/) is recommended, but not required.
- You have SSL certificates and DNS entries (both with and without wildcard respectively) for your host.

## System requirements

> Depending on how you plan to use shepard, the system requirements can vary greatly. While most services are relatively lightweight, the databases and shepard backend can be quite demanding. As a starting point, 8 GB of memory may be sufficient. Also, most services benefit greatly from many CPU cores, so there should be at least 4 cores/8 threads. The amount of disk space you need depends directly on the size of the data you want to manage with shepard.

- [neo4j system requirements](https://neo4j.com/docs/operations-manual/current/installation/requirements/#deployment-requirements-hardware)
- [InfluxDB system requirements](https://docs.influxdata.com/influxdb/v1.8/guides/hardware_sizing/#influxdb-oss-guidelines)
- [MongoDB system requirements](https://www.mongodb.com/blog/post/performance-best-practices-hardware-and-os-configuration)

## Installation

> These installation instructions result in a complete and opinionated environment with subdomains and SSL. The databases are configured to be directly accessible over the network for debugging purposes. A customized installation with a different configuration is easily possible and should be considered.

### 1. Clone repository

```bash
git clone https://gitlab.com/dlr-shepard/deployment.git
cd deployment
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

### 4. Check configuration in `docker-compose.yml` and especially check available memory

- Backend:

```yaml
CATALINA_OPTS: "-Xms2G -Xmx2G
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

- InfluxBD:

```yaml
INFLUXDB_DATA_CACHE_MAX_MEMORY_SIZE: 2G
```

### 5. Copy the file `env.example` to `.env`

```bash
# copy configuration file
cp env.example .env
```

### 6. Set passwords and configuration in `.env` the file

- All variables except `OIDC_ROLE` must be set!
- URLs have to end with a trailing slash
- The database passwords can be changed arbitrarily at the beginning

| Variable | Description | Example |
| --- | --- | --- |
| BACKEND_URL | contains the URL of the backend to be accessed by the clients | `https://backend.shepard.example.com/` |
| NEO4J_PW | initial neo4j password |  |
| MONGO_PW | initial mongodb password |  |
| INFLUX_PW | initial influxdb password |  |
| OIDC_AUTHORITY | is the URL of the oidc identity provider, which can be accessed by both the users and the shepard backend | `https://keycloak.example.com/realms/master/` |
| OIDC_PUBLIC | is the public key of the signature of the oidc identity provider (e.g. keycloak) | `MII...` |
| OIDC_ROLE | allows to restrict access to users with a specific realm role | see [restrict access to users with specific roles](#restrict-access-to-users-with-specific-roles) |
| CLIENT_ID | is the client ID of the frontend as known to the oidc identity provider | `shepard-frontend-dev` |

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

## Start

Make sure that all requested resources are available. In particular, check the free memory, since the shepard backend and the databases will use a lot of it. You can adjust the maximum amount of used memory in `docker-compose.yml`.

```bash
docker-compose pull
docker-compose up -d
```

You can find the backend logs in `/opt/shepard/backend/tomcat/shepard.log`.

## Update

Always check [recently merged Merge Requests](https://gitlab.com/dlr-shepard/deployment/-/merge_requests?label_name%5B%5D=Breaking+Change&scope=all&sort=merged_at_desc&state=merged) with the `Breaking Change` label before updating the system, as some changes may require manual intervention.

Database upgrades may also require manual intervention. What exactly needs to be adjusted can be found in the respective changelogs. The shepard backend can handle a relatively wide range of database versions. Therefore, it is possible to sit out database upgrades for some time and still update shepard. This repository will always use a recent version of the respective databases that have been successfully tested to work with shepard.

The upgrade process consists of shutting down the docker containers, updating the git repository, and restarting the docker containers again.

```bash
docker-compose down
git pull
# Check recent changes and adjust your configuration accordingly
docker-compose pull
docker-compose up -d
```

## Troubleshooting

### Check for Breaking Changes

Sometimes the installation does not work as expected or the system does not boot after an update. In these cases, you should check for `Breaking Changes` again, as you might have missed an important change.

### Review your configuration

Verify that the configuration meets the given requirements. The file must have the name `.env` and all variables from `env.example` must be set. Also look at the provided URLs, as all URLs must end with a trailing slash.

### Read the logs

Most containers log to `STDOUT`. Therefore, you can observe the logs via `docker-compose logs <containername>`.

The shepard backend also uses this method, but additionally writes to log files. These log files contain detailed log messages from the system and may contain important information about an issue. You can find the log files at `/opt/shepard/backend/tomcat/` unless you have changed the default location. The file `shepard.log` contains all logs since the last startup or rollover.

Some issues can be found in the frontend log. Open the web dev console (F12 in most browsers) and see whether there are errors in the log.

## Common issues

> TODO
