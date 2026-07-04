"""Minimal Shepard v2 REST client (appId-native, X-API-KEY auth).

Every ``feat-<slug>/seed.py`` in this directory builds on this client. It talks
only to the fork's ``/v2/`` surface (unified ``/v2/references?kind=`` +
``/v2/containers?kind=``, ``/v2/collections``, ``/v2/annotations``,
``/v2/templates``, multipart ``/v2/files``) with the same ``X-API-KEY`` header
the LUMEN seeder uses. No generated SDK, no third-party deps — stdlib ``urllib``
only, so a fresh checkout can run ``python seed.py`` with nothing installed.

Auth
----
Pass ``--apikey`` / ``--host`` on the CLI, or set ``API_KEY`` / ``BACKEND_URL``.
The key is a Shepard JWT API key (instance-admin for template/annotation writes).
The canonical way to mint one on the live instance::

    KC=https://shepard-auth.nuclide.systems; REALM=shepard-demo
    TOKEN=$(curl -s -X POST "$KC/realms/$REALM/protocol/openid-connect/token" \
      -d grant_type=password -d client_id=shepard -d username=... -d password=... \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
    SUB=$(curl -s "https://shepard-api.nuclide.systems/shepard/api/users/me" \
      -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;print(json.load(sys.stdin)['subject'])")
    curl -s -X POST "https://shepard-api.nuclide.systems/shepard/api/users/$SUB/apikeys" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
      -d '{"name":"feat-showcase-seeder-key"}' \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['jwt'])"

The full-instance wipe preserved the ``:User`` / ``:ApiKey`` / ``:Permissions``
nodes, so an existing seeder key keeps working after a re-bootstrap.

appId-only
----------
Every identifier in / out is the UUID v7 ``appId`` — never the numeric Neo4j id.
The lone exception is the v2 timeseries-reference create body, which still wants
the numeric ``timeseriesContainerId`` (a v1 leak through the
``TimeseriesReferenceIO``); ``create_timeseries_reference`` resolves it from the
container's v2 response so callers still only ever hold appIds. Logged as a
RESEED-FIND.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


class ShepardError(RuntimeError):
    def __init__(self, status: int, body: str, url: str):
        super().__init__(f"HTTP {status} on {url}: {body[:300]}")
        self.status = status
        self.body = body
        self.url = url


def _v2_base(host: str) -> str:
    """Return the backend root that prefixes ``/v2``. Strips a trailing
    ``/shepard/api`` so callers can pass either base."""
    h = host.rstrip("/")
    for suffix in ("/shepard/api", "/shepard/api/"):
        if h.endswith(suffix):
            return h[: -len(suffix)]
    return h


class Client:
    """Tiny appId-native v2 client."""

    def __init__(self, host: str, apikey: str):
        self.base = _v2_base(host)
        self.apikey = apikey

    # -- low-level ---------------------------------------------------------
    def _request(
        self,
        method: str,
        path: str,
        *,
        body: Any = None,
        query: dict | None = None,
        accept: str = "application/json",
    ) -> Any:
        url = f"{self.base}{path}"
        if query:
            url = f"{url}?{urllib.parse.urlencode(query)}"
        headers = {"X-API-KEY": self.apikey, "Accept": accept}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8")
                if not raw:
                    return None
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            raise ShepardError(e.code, e.read().decode("utf-8", "replace"), url) from e

    def get(self, path: str, query: dict | None = None) -> Any:
        return self._request("GET", path, query=query)

    def post(self, path: str, body: Any = None, query: dict | None = None) -> Any:
        return self._request("POST", path, body=body, query=query)

    def put(self, path: str, body: Any = None) -> Any:
        return self._request("PUT", path, body=body)

    def patch(self, path: str, body: Any = None) -> Any:
        return self._request("PATCH", path, body=body)

    def upload_multipart(
        self, path: str, *, query: dict, field: str, filename: str, content: bytes, content_type: str
    ) -> Any:
        """Multipart ``POST`` — the canonical one-file path. Binary kinds are NOT
        created via ``/v2/references?kind=file`` (that rejects with 400); this
        multipart entry point (``/v2/files`` or the video-reference path) is the
        canonical one."""
        url = f"{self.base}{path}?{urllib.parse.urlencode(query)}"
        boundary = f"----feat{uuid.uuid4().hex}"
        pre = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
        post = f"\r\n--{boundary}--\r\n".encode("utf-8")
        payload = pre + content + post
        headers = {
            "X-API-KEY": self.apikey,
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        }
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raise ShepardError(e.code, e.read().decode("utf-8", "replace"), url) from e

    # -- high-level helpers ------------------------------------------------
    def find_collection_by_name(self, name: str) -> dict | None:
        rows = self.get("/v2/collections") or []
        rows = rows if isinstance(rows, list) else rows.get("content", rows.get("results", []))
        for c in rows:
            if isinstance(c, dict) and c.get("name") == name:
                return c
        return None

    def ensure_collection(self, name: str, description: str, reset: bool = False) -> dict:
        existing = self.find_collection_by_name(name)
        if existing and reset:
            self.delete_collection(existing["appId"])
            existing = None
        if existing:
            return existing
        return self.post("/v2/collections", {"name": name, "description": description})

    def delete_collection(self, app_id: str) -> None:
        try:
            self._request("DELETE", f"/v2/collections/{app_id}")
        except ShepardError as e:
            if e.status not in (404,):
                raise

    def create_data_object(
        self, collection_app_id: str, name: str, *, description: str = "",
        attributes: dict | None = None, typed_predecessors: list | None = None,
    ) -> dict:
        body: dict = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = attributes
        if typed_predecessors:
            body["typedPredecessors"] = typed_predecessors
        return self.post(f"/v2/collections/{collection_app_id}/data-objects", body)

    def instantiate_template(
        self, collection_app_id: str, template_app_id: str, name: str,
        attributes: dict | None = None,
    ) -> dict:
        body: dict = {"name": name}
        if attributes:
            body["attributes"] = attributes
        return self.post(
            f"/v2/collections/{collection_app_id}/data-objects/from-template/{template_app_id}", body
        )

    def find_template_by_name(self, name: str, kind: str = "DATAOBJECT_RECIPE") -> dict | None:
        rows = self.get("/v2/templates", {"kind": kind}) or []
        rows = rows if isinstance(rows, list) else rows.get("content", rows.get("results", []))
        for t in rows:
            if isinstance(t, dict) and t.get("name") == name:
                return t
        return None

    def annotate(
        self, subject_app_id: str, subject_kind: str, predicate_iri: str,
        *, literal: str | None = None, object_iri: str | None = None,
        numeric: float | None = None, label: str | None = None,
    ) -> dict:
        body: dict = {
            "subjectAppId": subject_app_id,
            "subjectKind": subject_kind,
            "predicateIri": predicate_iri,
        }
        if literal is not None:
            body["objectLiteral"] = literal
        if object_iri is not None:
            body["objectIri"] = object_iri
        if numeric is not None:
            body["numericValue"] = numeric
        if label is not None:
            body["predicateLabel"] = label
        return self.post("/v2/annotations", body)

    def get_annotations(self, subject_app_id: str) -> list:
        try:
            rows = self.get("/v2/annotations", {"subjectAppId": subject_app_id}) or []
        except ShepardError as e:
            # Some subject kinds (e.g. a FileReference) gate the annotation read
            # behind a Read permission the seeder key doesn't hold on that entity.
            if e.status in (403, 404):
                return []
            raise
        return rows if isinstance(rows, list) else rows.get("content", rows.get("results", []))

    def upload_file(self, parent_data_object_app_id: str, name: str, content: bytes,
                    content_type: str = "application/octet-stream") -> dict:
        """Singleton FileReference — POST /v2/files (one file → singleton, never
        a FileBundle; per CLAUDE.md)."""
        return self.upload_multipart(
            "/v2/files",
            query={"parentDataObjectAppId": parent_data_object_app_id, "name": name},
            field="file", filename=name, content=content, content_type=content_type,
        )

    def create_timeseries_container(self, name: str) -> dict:
        return self.post("/v2/containers", {"name": name}, query={"kind": "timeseries"})

    def create_timeseries_reference(
        self, data_object_app_id: str, name: str, container: dict,
        channels: list[dict], start_millis: int, end_millis: int,
    ) -> dict:
        """Mint a TimeseriesReference. ``container`` is the v2 container dict;
        we resolve its numeric ``id`` for the v1-leaked ``timeseriesContainerId``
        body field (RESEED-FIND)."""
        body = {
            "name": name,
            "start": start_millis,
            "end": end_millis,
            "timeseriesContainerId": container["id"],
            "timeseries": channels,
        }
        return self.post(
            "/v2/references", body, query={"kind": "timeseries", "dataObjectAppId": data_object_app_id}
        )

    def plugin_states(self) -> dict[str, str]:
        try:
            d = self.get("/v2/admin/plugins") or {}
        except ShepardError:
            return {}
        return {p["id"]: p.get("state", "") for p in d.get("plugins", [])}


def add_common_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--host", default=os.environ.get("BACKEND_URL", "http://localhost:8080"),
        help="Shepard backend root or /shepard/api base URL.",
    )
    parser.add_argument(
        "--apikey", default=os.environ.get("API_KEY", ""),
        help="Shepard JWT API key (or set API_KEY env var). See _client.py docstring to mint one.",
    )
    parser.add_argument("--reset", action="store_true", help="Delete + recreate the feat-* collection.")


def client_from_args(args: argparse.Namespace) -> Client:
    apikey = args.apikey
    if not apikey:
        # Convenience: pick up the reseed key file if present.
        for p in ("/tmp/reseed_apikey.txt",):
            if os.path.exists(p):
                apikey = open(p).read().strip()
                break
    if not apikey:
        print("ERROR: no API key. Pass --apikey or set API_KEY.", file=sys.stderr)
        sys.exit(2)
    return Client(args.host, apikey)


def guess_content_type(filename: str, default: str = "application/octet-stream") -> str:
    return mimetypes.guess_type(filename)[0] or default
