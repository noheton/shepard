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
import re
from datetime import datetime, timezone
from urllib.parse import unquote, urlparse

c = get_config()  # noqa: F821  — jupyterhub injects `get_config` at runtime


# --------------------------------------------------------------------- #
# J1e-PR-06-AUTOFETCH-01 — pre-spawn helpers
#
# The `pre_spawn_hook` below reads `?file=<url>` from the spawn request,
# validates it against an operator-configured allowlist, fetches the
# bytes using the user's OIDC access token, and writes them into the
# user's notebook volume at `/shepard-imports/<filename>` so they appear
# at `/home/jovyan/work/shepard-imports/<filename>` once the kernel starts.
#
# All four helpers below are module-level pure functions so they're
# importable + unit-testable from `plugins/jupyter/tests/`. The hook
# itself stays inside this file.
# --------------------------------------------------------------------- #

_FILENAME_STAR_RE = re.compile(
    r"filename\*\s*=\s*(?P<charset>[^']*)'(?P<lang>[^']*)'(?P<value>[^;]+)",
    re.IGNORECASE,
)
_FILENAME_RE = re.compile(
    r'filename\s*=\s*"?(?P<value>[^";]+)"?',
    re.IGNORECASE,
)


def _default_allowed_hosts() -> set[str]:
    """Parse `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` (comma-separated) into a set."""
    raw = os.environ.get(
        "JUPYTERHUB_SHEPARD_ALLOWED_HOSTS",
        "shepard.nuclide.systems,shepard-api.nuclide.systems",
    )
    return {h.strip().lower() for h in raw.split(",") if h.strip()}


def is_url_allowed(url: str, allowed_hosts: set[str]) -> bool:
    """Return True iff `url` parses cleanly to an https/http URL whose
    hostname is in `allowed_hosts`. SSRF defense: anything else returns
    False WITHOUT triggering a fetch."""
    if not url or not isinstance(url, str):
        return False
    try:
        parsed = urlparse(url)
    except (ValueError, TypeError):
        return False
    if parsed.scheme not in ("http", "https"):
        return False
    host = (parsed.hostname or "").lower()
    if not host:
        return False
    return host in allowed_hosts


def sanitize_filename(name: str, max_length: int = 255) -> str:
    """Strip path traversal segments and bound the length. Never returns
    an empty string — falls back to `download.bin` for edge cases."""
    if not name:
        return "download.bin"
    # Cut to basename — drop any path component, forward or backward.
    name = name.replace("\\", "/").split("/")[-1]
    # Strip traversal dots; collapse leading dots that hide files.
    name = name.lstrip(".") or "download.bin"
    # Drop NULs + control chars.
    name = re.sub(r"[\x00-\x1f]", "", name)
    if not name or name in (".", ".."):
        return "download.bin"
    if len(name) > max_length:
        # Preserve the extension if there is one.
        if "." in name:
            stem, _, ext = name.rpartition(".")
            ext = ext[:32]  # cap extension length too
            keep = max_length - len(ext) - 1
            name = f"{stem[:keep]}.{ext}" if keep > 0 else name[:max_length]
        else:
            name = name[:max_length]
    return name


def derive_filename(url: str, content_disposition: str | None) -> str:
    """Pick a filename: Content-Disposition first (RFC 5987 then plain),
    then URL path tail, then `download.bin`. Always sanitized."""
    if content_disposition:
        # RFC 5987: filename*=UTF-8''my%20file.pdf
        m = _FILENAME_STAR_RE.search(content_disposition)
        if m:
            try:
                return sanitize_filename(unquote(m.group("value").strip()))
            except (UnicodeDecodeError, ValueError):
                pass
        m = _FILENAME_RE.search(content_disposition)
        if m:
            return sanitize_filename(m.group("value").strip())
    # Fall back to URL path tail.
    try:
        parsed = urlparse(url)
        tail = unquote((parsed.path or "").rstrip("/").split("/")[-1])
        if tail:
            return sanitize_filename(tail)
    except (ValueError, TypeError):
        pass
    return "download.bin"


def render_readme(
    filename: str,
    source_url: str,
    status: str,
    fetched_at: datetime | None = None,
) -> str:
    """Build the per-import README sidecar. `status` is one of
    `OK`, `401`, `403`, `allowlist-miss`, `timeout`, `no-token`, or a
    free-form short error string."""
    ts = (fetched_at or datetime.now(timezone.utc)).isoformat()
    return (
        f"# Shepard import — {filename}\n"
        f"- Source URL: {source_url}\n"
        f"- Fetched at: {ts}\n"
        f"- Status: {status}\n"
        f"- This is a pre-fetch. Modifications stay in this notebook "
        f"volume — they do NOT write back to Shepard.\n"
    )


def _pre_spawn_hook(spawner):  # pragma: no cover — exercised live in JH
    """Capture `?file=<url>` from the spawn URL and fetch into the user
    volume. Secondary-write: all failures are caught, logged WARN, and
    surfaced via a README sidecar — the kernel always spawns."""
    import asyncio
    import docker
    import requests

    log = spawner.log
    file_urls: list[str] = []

    # Source 1: Tornado handler query string (the `?file=` case).
    handler = getattr(spawner, "handler", None)
    if handler is not None:
        try:
            file_urls = handler.get_arguments("file") or []
        except Exception as exc:  # noqa: BLE001
            log.warning("J1e-PR-06: failed to read ?file= arguments: %s", exc)

    # Source 2: user_options (form-submitted).
    if not file_urls:
        opt = (spawner.user_options or {}).get("file")
        if isinstance(opt, str):
            file_urls = [opt]
        elif isinstance(opt, list):
            file_urls = [u for u in opt if isinstance(u, str)]

    if not file_urls:
        return  # nothing to do — common case

    allowed = _default_allowed_hosts()

    # Retrieve the OIDC access token from auth_state.
    access_token = ""
    try:
        auth_state = asyncio.get_event_loop().run_until_complete(
            spawner.user.get_auth_state()
        ) if not asyncio.get_event_loop().is_running() else None
    except Exception:  # noqa: BLE001
        auth_state = None
    # In JH's async pre_spawn context the event loop IS running — use
    # the awaitable form via `await` if we're in a coroutine, else fall
    # back to spawner.environment which the auth_state_hook already set.
    if access_token == "":
        access_token = (spawner.environment or {}).get(
            "SHEPARD_OIDC_ACCESS_TOKEN", ""
        )

    # Resolve the user's notebook volume on the host filesystem.
    username = spawner.user.name
    volume_name = f"jupyterhub-user-{username}"
    try:
        client = docker.from_env()
        try:
            volume = client.volumes.get(volume_name)
        except docker.errors.NotFound:
            volume = client.volumes.create(name=volume_name)
        mountpoint = volume.attrs["Mountpoint"]
    except Exception as exc:  # noqa: BLE001
        log.warning("J1e-PR-06: cannot resolve user volume %s: %s", volume_name, exc)
        return

    import_dir = os.path.join(mountpoint, "shepard-imports")
    try:
        os.makedirs(import_dir, exist_ok=True)
    except OSError as exc:
        log.warning("J1e-PR-06: cannot create %s: %s", import_dir, exc)
        return

    for raw_url in file_urls:
        if not is_url_allowed(raw_url, allowed):
            host = urlparse(raw_url).hostname if raw_url else "<empty>"
            log.warning("J1e-PR-06: allowlist miss for host=%s", host)
            placeholder = sanitize_filename(
                derive_filename(raw_url or "", None)
            )
            try:
                with open(
                    os.path.join(import_dir, f"README-{placeholder}.md"),
                    "w",
                    encoding="utf-8",
                ) as fh:
                    fh.write(render_readme(placeholder, raw_url, "allowlist-miss"))
            except OSError:
                pass
            continue

        if not access_token:
            log.warning("J1e-PR-06: no access token in auth_state; skipping fetch")
            placeholder = derive_filename(raw_url, None)
            try:
                with open(
                    os.path.join(import_dir, f"README-{placeholder}.md"),
                    "w",
                    encoding="utf-8",
                ) as fh:
                    fh.write(render_readme(placeholder, raw_url, "no-token"))
            except OSError:
                pass
            continue

        status = "OK"
        filename = derive_filename(raw_url, None)
        try:
            resp = requests.get(
                raw_url,
                headers={"Authorization": f"Bearer {access_token}"},
                stream=True,
                timeout=(10, 300),
                allow_redirects=False,
            )
            filename = derive_filename(
                raw_url, resp.headers.get("Content-Disposition")
            )
            if resp.status_code != 200:
                status = str(resp.status_code)
                log.warning(
                    "J1e-PR-06: fetch host=%s status=%s",
                    urlparse(raw_url).hostname,
                    resp.status_code,
                )
            else:
                target = os.path.join(import_dir, filename)
                with open(target, "wb") as out:
                    for chunk in resp.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            out.write(chunk)
                log.info(
                    "J1e-PR-06: fetched host=%s -> %s",
                    urlparse(raw_url).hostname,
                    filename,
                )
        except requests.Timeout:
            status = "timeout"
            log.warning("J1e-PR-06: timeout fetching host=%s", urlparse(raw_url).hostname)
        except Exception as exc:  # noqa: BLE001
            status = f"error: {type(exc).__name__}"
            log.warning(
                "J1e-PR-06: fetch error host=%s err=%s",
                urlparse(raw_url).hostname,
                exc,
            )

        try:
            with open(
                os.path.join(import_dir, f"README-{filename}.md"),
                "w",
                encoding="utf-8",
            ) as fh:
                fh.write(render_readme(filename, raw_url, status))
        except OSError as exc:
            log.warning("J1e-PR-06: README write failed: %s", exc)

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

# --------------------------------------------------------------------- #
# J1e-PR-06-AUTOFETCH-01 — wire the pre-spawn hook + options_from_form
# so `?file=<url>` is captured into `spawner.user_options["file"]`.
# --------------------------------------------------------------------- #


def _options_from_form(form_data, spawner=None):
    """Map JupyterHub spawn form values into `user_options`. We accept a
    list of `file` values so an operator can extend to bulk later. The
    request's query-string `?file=` arrives here as `form_data['file']`
    because Tornado's RequestHandler.get_arguments aggregates both."""
    files = form_data.get("file", []) if isinstance(form_data, dict) else []
    if isinstance(files, str):
        files = [files]
    return {"file": [f for f in files if isinstance(f, str) and f]}


c.Spawner.options_from_form = _options_from_form
c.Spawner.pre_spawn_hook = _pre_spawn_hook
