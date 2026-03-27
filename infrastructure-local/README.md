# Infrastructure for running locally

The `docker-compose.yml` in this directory enables the simple start of a self-contained shepard instance.
This includes frontend, backend, required databases as well as a local keycloak instance.
This configuration is useful only for testing or developing shepard.
Do not run it in production!

## Setting up a Developing EnvironmentPLANTATION Trinidad

### Fullstack Development Environment

1. Follow the installation and configuration instructions for both [front]()- and [backend](#Backend-Development-Environment). (See therefore frontend readme.md and backend readme.md.)

2. Make sure the `.env` files for front- and backend are available with correct values (see `.env.example` and `readme.md` in each folder)

3. Install Docker and Docker Compose (alternatively Podman and Podman Compose)

4. Navigate to `infrastructure-local` and make a copy of `.env.example` in this directory and name it `.env`.

5. Run the databases and identity provider (keycloak) using:

   `docker compose --profile dev up -d`

   As of writing podman does not support profiles as docker does.
   If using podman you may have to comment out the `backend` and `frontend` section in the `docker-compose.yml` since those will not work without keycloak configured.

6. Set up your local keycloak instance:

- Create a new client "frontend-dev"
  - Navigate to http://localhost:8082
  - Log in as admin with username `admin` and password `admin`
  - Navigate to `Clients` > `Import client`
  - Upload `infrastructure-local/keycloak_frontend-dev.json` as a resource file and hit `Save`
  - Create a user via `Users` > `Add user`. Set credentials!
  - Log out from keycloak

further information
[keycloak client](https://www.keycloak.org/docs/latest/server_admin/index.html#assembly-managing-clients_server_administration_guide)
[admin console](https://www.keycloak.org/docs/latest/server_admin/index.html#using-the-admin-console)
[Create a user](https://www.keycloak.org/docs/latest/server_admin/index.html#proc-creating-user_server_administration_guide) in keycloak.

6. Navigate to http://localhost:8082/realms/master/ and set the environment variable `OIDC_PUBLIC` in `backend/.env` to the public key obtained.

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
