# JupyterHub config for shepard-plugin-jupyter (J1e-PR-05).
#
# This file is mounted read-only into the JupyterHub container at
# /srv/jupyterhub/jupyterhub_config.py.
#
# Authentication: GenericOAuth against the same Keycloak realm Shepard's
# backend uses.  Any authenticated Shepard user can spawn a notebook —
# fine-grained access to specific Shepard data is enforced at the
# REST-API layer (the user's OIDC token is forwarded to the kernel and
# used for every Shepard call).
#
# Spawner: DockerSpawner — JupyterHub spins up one per-user notebook
# container from the image declared in DOCKER_NOTEBOOK_IMAGE.
#
# Operator override: an operator wanting LDAP, GitHub OAuth, or
# KubeSpawner replaces the relevant block; the rest of the file is
# generic.

import os

c = get_config()  # noqa: F821  — jupyterhub injects `get_config` at runtime

# --------------------------------------------------------------------- #
# OIDC (GenericOAuth) authenticator pointed at the Keycloak realm
# Shepard's backend already trusts.
# --------------------------------------------------------------------- #
c.JupyterHub.authenticator_class = "generic-oauth"

issuer_url = os.environ["SHEPARD_OIDC_ISSUER_URL"].rstrip("/")
# Split-horizon OIDC: the browser hits the public hostname for the
# authorize redirect, but JH's server-to-server token + userinfo
# exchange happens inside the compose network — the JH container
# cannot route to the public Zoraxy IP for back-channel calls.
# Operator sets SHEPARD_OIDC_INTERNAL_ISSUER_URL to the internal
# compose hostname (e.g. http://keycloak:8082/realms/shepard-demo);
# falls back to the public issuer if unset (works on deployments
# where the front + back channel share a network path).
internal_issuer_url = os.environ.get(
    "SHEPARD_OIDC_INTERNAL_ISSUER_URL", issuer_url
).rstrip("/")
# Path-mount under /jupyterhub on shepard.nuclide.systems
# (per CLAUDE.md "Always: mount plugin UI sidecars as paths" rule).
# JupyterHub's c.JupyterHub.base_url is the canonical path-mount knob;
# single-user notebook URLs become /jupyterhub/user/<name>/... automatically.
c.JupyterHub.base_url = "/jupyterhub"
public_url = os.environ.get(
    "JUPYTERHUB_PUBLIC_URL", "https://shepard.nuclide.systems/jupyterhub"
).rstrip("/")

c.GenericOAuthenticator.client_id = os.environ["JUPYTERHUB_KEYCLOAK_CLIENT_ID"]
c.GenericOAuthenticator.client_secret = os.environ["JUPYTERHUB_KEYCLOAK_CLIENT_SECRET"]
c.GenericOAuthenticator.oauth_callback_url = f"{public_url}/hub/oauth_callback"

# Front-channel (browser-visible) endpoint — public URL.
c.GenericOAuthenticator.authorize_url = f"{issuer_url}/protocol/openid-connect/auth"
# Back-channel (server-to-server) endpoints — internal compose URL when set.
c.GenericOAuthenticator.token_url = f"{internal_issuer_url}/protocol/openid-connect/token"
c.GenericOAuthenticator.userdata_url = f"{internal_issuer_url}/protocol/openid-connect/userinfo"

# Standard OIDC scopes; `email` + `profile` give us the username.
c.GenericOAuthenticator.scope = ["openid", "email", "profile"]
c.GenericOAuthenticator.username_key = "preferred_username"

# Any authenticated Shepard user may spawn a notebook.  Per-DataObject
# access control happens via the Shepard REST API the kernel calls
# (the user's OIDC token is forwarded — see DOCKER_NOTEBOOK_ARGS below).
c.GenericOAuthenticator.allow_all = True

# --------------------------------------------------------------------- #
# Spawner — DockerSpawner for the single-node compose deployment.
# --------------------------------------------------------------------- #
c.JupyterHub.spawner_class = "dockerspawner.DockerSpawner"
c.DockerSpawner.image = os.environ.get(
    "DOCKER_NOTEBOOK_IMAGE", "jupyter/scipy-notebook:python-3.11"
)
# Docker compose prefixes networks with the project name — `shepard` is the
# logical name declared in compose-profile.yml but the actual network is
# `<project>_shepard` (e.g. `infrastructure_shepard` on this deploy).
# Operator sets JUPYTERHUB_DOCKER_NETWORK to the resolved name in .env.
c.DockerSpawner.network_name = os.environ.get(
    "JUPYTERHUB_DOCKER_NETWORK", "infrastructure_shepard"
)
c.DockerSpawner.remove = True
c.DockerSpawner.notebook_dir = "/home/jovyan/work"
c.DockerSpawner.volumes = {"jupyterhub-user-{username}": "/home/jovyan/work"}

# Forward the user's OIDC token + Shepard base URL into the kernel
# environment so `shepard-py` can authenticate against the backend
# transparently.
c.Spawner.environment = {
    "SHEPARD_BACKEND_URL": os.environ.get("SHEPARD_BACKEND_URL", "http://backend:8080"),
}
c.Spawner.auth_state_hook = lambda spawner, auth_state: spawner.environment.update(
    {"SHEPARD_OIDC_ACCESS_TOKEN": (auth_state or {}).get("access_token", "")}
)
c.GenericOAuthenticator.enable_auth_state = True

# --------------------------------------------------------------------- #
# Hub bind + DB
# --------------------------------------------------------------------- #
c.JupyterHub.bind_url = "http://:8000"
c.JupyterHub.hub_ip = "0.0.0.0"
c.JupyterHub.hub_connect_ip = "jupyterhub"
c.ConfigurableHTTPProxy.api_url = "http://127.0.0.1:8001"
c.JupyterHub.db_url = "sqlite:////data/jupyterhub.sqlite"
c.JupyterHub.cookie_secret_file = "/data/jupyterhub_cookie_secret"
