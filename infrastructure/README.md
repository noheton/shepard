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
- [InfluxDB system requirements](https://docs.influxdata.com/influxdb/v1.8/guides/hardware_sizing/#influxdb-oss-guidelines)
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

- InfluxDB:

```yaml
INFLUXDB_DATA_CACHE_MAX_MEMORY_SIZE: 2G
```

### 6. Copy the file `.env.example` to `.env`

```bash
# copy configuration file
cp .env.example .env
```

### 7. Set passwords and configuration in the `.env` file

- All variables except `OIDC_ROLE` must be set!
- URLs have to end with a trailing slash
- The database passwords can be changed arbitrarily at the beginning

| Variable            | Description                                                                                               | Example                                                                                           |
| ------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| BACKEND_URL         | contains the URL of the backend to be accessed by the clients                                             | `https://backend.shepard.example.com/`                                                            |
| NEO4J_PW            | initial Neo4j password                                                                                    |                                                                                                   |
| MONGO_ROOT_USERNAME | MongoDB admin name (automatically created on a fresh instance)                                            |                                                                                                   |
| MONGO_ROOT_PASSWORD | MongoDB admin password (automatically created on a fresh instance)                                        |                                                                                                   |
| MONGO_DATABASE      | MongoDB database name for shepard (automatically created on a fresh instance)                             |                                                                                                   |
| MONGO_USERNAME      | MongoDB non-admin user username for `MONGO_DATABASE` database                                             |                                                                                                   |
| MONGO_PW            | MongoDB non-admin user password for `MONGO_DATABASE` database                                             |
| INFLUX_PW           | initial InfluxDB password                                                                                 |                                                                                                   |
| POSTGRES_PW         | postgres user password                                                                                    |                                                                                                   |
| OIDC_AUTHORITY      | is the URL of the oidc identity provider, which can be accessed by both the users and the shepard backend | `https://keycloak.example.com/realms/master/`                                                     |
| OIDC_PUBLIC         | is the public key of the signature of the oidc identity provider (e.g. keycloak)                          | `MII...`                                                                                          |
| OIDC_ROLE           | allows to restrict access to users with a specific realm role                                             | see [restrict access to users with specific roles](#restrict-access-to-users-with-specific-roles) |
| CLIENT_ID           | is the client ID of the frontend as known to the oidc identity provider                                   | `example-client-id`                                                                               |
| Variable            | Description                                                                                               | Example                                                                                           |

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

## Restrict public access to Chronograph

Chronograf by default is publicly accessible! You can configure it with OAuth or simply with a password and username. More information can be found [here](https://docs.influxdata.com/chronograf/v1/administration/managing-security/).

## Start

Make sure that all requested resources are available. In particular, check the free memory, since the shepard backend and the databases will use a lot of it. You can adjust the maximum amount of used memory in `docker-compose.yml`.

```bash
docker compose pull
docker compose up -d
```

You can find the backend logs in `/opt/shepard/backend/logs`.

## Experimental features

There is a `docker-compose-exp.yml` file that holds a deployment setup with experimental extensions.
To be able to try those extensions, the deployment should be run using this file instead.

### Standalone Nuxt frontend (v2.0.0)

- Adapt the proxy config by adding the subdomain mapping the the Caddy file.

```
https://nuxtend.hostname_placeholder_do_not_change {
	reverse_proxy nuxtend:3000

	tls /etc/caddy/ssl/shepard.crt /etc/caddy/ssl/shepard.key
}
```

- Add the new link the index page

```html
<a href="https://nuxtend.hostname_placeholder_do_not_change/"> https://nuxtend.hostname_placeholder_do_not_change/ </a>
```

- Add the experimental environment variables in `.env`

```
FRONTEND_URL='Frontend URL' # (should end with '/')
FRONTEND_AUTH_SECRET='Frontend auth secret'
```

> **_NOTE:_** The `FRONTEND_AUTH_SECRET` could be any random generated string which will be used to hash JWT tokens. you can quickly create a good value on the command line using `openssl`
>
> ```bash
> $ openssl rand -base64 32
> ```

### TimescaleDB

There is an experimental database to store the timeseries data used as replacement for InfluxDB.
This database will be experimental until the migration from InfluxDB is completely layed down.
Running this database requires changes to directory permissions like mentioned in [here](#4-create-necessary-directories)

### Run the experimental docker compose file

```bash
docker compose -f .\docker-compose-exp.yml up -d
```

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
