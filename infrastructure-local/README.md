# Infrastructure for running locally

The `docker-compose.yml` in this directory enables the simple start of a self-contained shepard instance.
This includes frontend, backend, required databases as well as a local keycloak instance.
This configuration is useful only for testing or developing shepard.
Do not run it in production!

## Development configuration

If you want to develop shepard you will probably want to run backend and frontend elsewhere.
To do this execute the following steps:

1. Navigate to `infrastructure-local`.

2. Copy `.env.example` in this directory and name the copy `.env`.

3. Run the databases and identity provider (keycloak) using:

   `docker compose --profile dev up -d`

   As of writing podman does not support profiles as docker does.
   If using podman you may have to comment out the `backend` and `frontend` section in the `docker-compose.yml` since those will not work without keycloak configured.

4. Create a new [client](https://www.keycloak.org/docs/latest/server_admin/index.html#assembly-managing-clients_server_administration_guide) "frontend-dev" at the keycloak master realm using the [admin console](https://www.keycloak.org/docs/latest/server_admin/index.html#using-the-admin-console) (http://localhost:8082/). Username and password are "admin" each.
   Go to "Clients", "Import client" and use the source file `keycloak_frontend-dev.json`.

5. [Create a user](https://www.keycloak.org/docs/latest/server_admin/index.html#proc-creating-user_server_administration_guide) in keycloak.

6. Set the environment variable `OIDC_PUBLIC` in `.env` to the public key obtained from <http://localhost:8082/realms/master/>.

## Trying / testing shepard

After following the steps in the previous section you can now run

`docker compose --profile tryout up -d`

to start the shepard backend and frontend.
The frontend is then accessible at <http://localhost:3000/>.

## Monitoring

Some monitoring / database administration tools are optionally provided:

- [mongo-express](https://github.com/mongo-express/mongo-express)
- [Grafana](https://grafana.com/grafana/?plcmt=products-nav)
- [Prometheus](https://github.com/prometheus/prometheus)

Run them using:

`docker compose --profile monitoring up -d`
