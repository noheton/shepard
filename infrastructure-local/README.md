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

3. If you want to quickly try out shepard (i.e. run frontend and backend) run:

   `docker compose --profile tryout up -d`

   Alternatively if you want to start frontend and backend and only start keycloak and the databases run

   `docker compose --profile dev up -d`

4. There are now two users ready to use in shepard with usernames `ronald` and `patrik`.
   Password is `asdf` each.

## Trying / testing shepard

After following the steps in the previous section you can now run

to start the shepard backend and frontend.
The frontend is then accessible at <http://localhost:3000/>.

## Monitoring

Some monitoring / database administration tools are optionally provided:

- [mongo-express](https://github.com/mongo-express/mongo-express)
- [Grafana](https://grafana.com/grafana/?plcmt=products-nav)
- [Prometheus](https://github.com/prometheus/prometheus)

Run them using:

`docker compose --profile monitoring up -d`
