# Infrastructure for running locally

The `docker-compose.yml` in this directory enables the simple start of a self-contained shepard instance.
This includes frontend, backend, required databases as well as a local keycloak instance.
This configuration is useful only for testing or developing shepard.
Do not run it in production!

## Development configuration

If you want to develop shepard you will probably want to run backend and frontend elsewhere.
To do this execute the following steps:

1. Copy `.env.example` to `.env`.

2. Run the databases and identity provider (keycloak) using:

   `docker compose --profile dev up -d`

   As of writing podman does not support profiles as docker does.
   If using podman comment out the `backend` and `frontend`section in the `docker-compose.yml`.

3. Import the client "frontend-dev" to the keycloak master realm using the file `frontend-dev.json`.

4. Create a user in keycloak.
5. Set the environment variable `OIDC_PUBLIC` in `.env` to the public key obtained from <http://localhost:8082/realms/master/>.

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
