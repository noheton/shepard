# shepard deployment

This repository contains everything you need to set up a shepard instance with Docker and Docker Compose.
For more information about shepard, its usage and infrastructure, check out [the wiki](https://gitlab.com/dlr-shepard/documentation/-/wikis/home).

## Prerequisites

- [Docker](https://docs.docker.com/engine/) and [Docker Compose](https://docs.docker.com/compose/) are installed.
- A reverse proxy (e.g. [nginx](https://www.nginx.com/)) is installed.
- You have SSL certificates and DNS entries (both with and without wildcard respectively) for your host.
- There is an OpenID Connect identity provider that uses [JSON web tokens](https://jwt.io/) as access tokens. [Keycloak](https://www.keycloak.org/) is recommended, but not required.

## System requirements

Depending on how you plan to use shepard, the system requirements can vary greatly.
While most services are relatively lightweight, the databases and shepard backend can be quite demanding.
As a starting point, 8 GB of memory per service may be sufficient.
Also, most services benefit greatly from many CPU cores, so there should be at least 4 cores/8 threads.
The amount of disk space you need depends directly on the size of the data you want to manage with shepard.

- [neo4j system requirements](https://neo4j.com/docs/operations-manual/current/installation/requirements/#deployment-requirements-hardware)
- [InfluxDB system requirements](https://docs.influxdata.com/influxdb/v1.8/guides/hardware_sizing/#influxdb-oss-guidelines)
- [MongoDB system requirements](https://www.mongodb.com/blog/post/performance-best-practices-hardware-and-os-configuration)

## Installation

> These installation instructions result in a complete environment.
> The databases are configured to be directly accessible over the network for debugging purposes.
> A customized installation with a different configuration is easily possible and should be considered.

1. Clone repository

```bash
git clone https://gitlab.com/dlr-shepard/deployment.git
cd deployment
```

2. Prepare nginx
   - Apply for SSL certificates and store them in the system
   - Create Diffie-Hellman parameters

```bash
openssl dhparam -out /etc/nginx/dhparam.pem 2048
```

3. Set up nginx
   - Edit and enable the [sample files with self-signed certificates](https://gitlab.com/dlr-shepard/deployment/-/blob/master/etc/nginx/sites-available/) as needed
   - [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/#server=nginx&config=intermediate)
   - **Do not forget to change these files according to your certificates**
   - Edit `index.html` and put this file into the appropriate directory
   - Restart nginx afterwards by typing `systemctl restart nginx.service`

```bash
# nginx config, replace my.awesome.host.name with your real hostname
cp etc/nginx/sites-available/* /etc/nginx/sites-available
sed -i "s@HOSTNAME_PLACEHOLDER@my.awesome.host.name@" /etc/nginx/sites-available/*

# index.html, replace my.awesome.host.name with your real hostname
mkdir /var/www/shepard
cp var/www/shepard/index.html /var/www/shepard/index.html
sed -i "s@HOSTNAME_PLACEHOLDER@my.awesome.host.name@" /var/www/shepard/index.html

# enable all available sites
ln -s /etc/nginx/sites-available/* /etc/nginx/sites-enabled/
```

4. Check configuration in `docker-compose.yml` and especially check available memory
5. Copy the file `env.example` to `.env` and set passwords and configuration in this file
   - All variables except `OIDC_ROLE` must be set!
   - URLs have to end with a trailing slash
   - `BACKEND_URL` contains the URL of the backend (e.g. `https://backend.shepard.example.com/`)
   - The database passwords can be changed arbitrarily at the beginning
   - `OIDC_PUBLIC` is the public key of the oidc identity provider (e.g. keycloak)
   - `OIDC_AUTHORITY` is the URL of the oidc identity provider (e.g. `https://keycloak.example.com/auth/realms/master/`)
   - `OIDC_ROLE` allows to restrict access to users with a specific role (see [Restrict access to users with specific roles](#restrict-access-to-users-with-specific-roles))
   - `CLIENT_ID` is the client ID of the frontend as known to the oidc identity provider.
   - The public key and the URL of the OpenID Connect provider must be written into the corresponding variables

```bash
# copy configuration file
cp env.example .env
```

## Restrict access to users with specific roles

Some OpenID Connect identity providers such as [Keycloak](https://www.keycloak.org/) are able to add role information as part of access tokens.
The access token then contains an additional claim `realm_access` like the following:

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

The shepard backend can be configured to allow only users with a specific role.
To do so, the optional `OIDC_ROLE` variable in `.env` can be set to the given role.
From the next restart, users without this role will no longer be able to access shepard.

## Start

Make sure that all requested resources are available.
In particular, check the free memory, since the shepard backend and the databases will use a lot of it.
You can adjust the maximum amount of used memory in `docker-compose.yml`.

```bash
docker-compose pull
docker-compose up -d
```

You can find the backend logs in `/opt/shepard/backend/tomcat/shepard.log`.

## Update

Always check [recently merged Merge Requests](https://gitlab.com/dlr-shepard/deployment/-/merge_requests?scope=all&state=merged&label_name[]=Breaking%20Change) with the `Breaking Change` label before updating the system, as some changes may require manual intervention.

Database upgrades may also require manual intervention.
What exactly needs to be adjusted can be found in the respective changelogs.
The shepard backend can handle a relatively wide range of database versions.
Therefore, it is possible to sit out database upgrades for some time and still update shepard.
This repository will always use a recent version of the respective databases that have been successfully tested to work with shepard.

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

Sometimes the installation does not work as expected or the system does not boot after an update.
In these cases, you should check for `Breaking Changes` again, as you might have missed an important change.

### Review your configuration

Verify that the configuration meets the given requirements.
The file must have the name `.env` and all variables from `env.example` must be set.
Also look at the provided URLs, as most URLs must end with a trailing slash.

### Read the logs

Most containers log to `STDOUT`.
Therefore, you can observe the logs via `docker-compose logs <containername>`.
The shepard backend also uses this method, but additionally writes to log files.
These log files contain detailed log messages from the system and may contain important information about an issue.
You can find the log files at `/opt/shepard/backend/tomcat/` unless you have changed the default location.
The file `shepard.log` contains all logs since the last startup or rollover.
