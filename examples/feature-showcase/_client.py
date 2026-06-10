"""Minimal Shepard v2 REST client (appId-native, X-API-KEY auth).

Shared helper for the per-feature showcase seeds under
``examples/feature-showcase/feat-*/seed.py``.  Each seed proves ONE shipped
feature end-to-end with synthetic data (no real DLR IP), creating a small
``feat-<slug>`` Collection.

The client speaks the same ``X-API-KEY`` header the LUMEN seeder uses and
addresses every entity by its UUID-v7 ``appId`` (never the numeric Neo4j id),
per the project's appId-only invariant.

Auth + host are resolved (in order):

  1. CLI flags ``--host`` / ``--apikey``
  2. env vars ``BACKEND_URL`` / ``API_KEY``
  3. local defaults (``http://localhost:8080`` + ``/tmp/reseed_apikey.txt``)

``--host`` accepts either the backend root (``http://localhost:8080``) or the
``/shepard/api`` base; both the v1 (``/shepard/api``) and v2 (``/v2``) bases are
derived from it.

To mint an API key against a remote instance::

    KC=https://shepard-auth.nuclide.systems; REALM=shepard-demo
    TOKEN=$(curl -s -X POST \
      "$KC/realms/$REALM/protocol/openid-connect/token" \
      -d grant_type=password -d client_id=frontend-dev \
      -d username=admin -d password=admin-demo -d scope=openid \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
    curl -s -X POST https://shepard-api.nuclide.systems/shepard/api/users/$SUB/apikeys \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
      -d '{"name":"feat-showcase-seeder-key"}' \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['jwt'])"
"""

from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_HOST = "http://localhost:8080"
DEFAULT_KEY_FILE = "/tmp/reseed_apikey.txt"


def _norm_root(host: str) -> str:
    """Normalise any of {root, /shepard/api, /shepard/api/} to the backend root."""
    h = host.rstrip("/")
    for suffix in ("/shepard/api", "/v2"):
        if h.endswith(suffix):
            h = h[: -len(suffix)]
    return h.rstrip("/")


class HttpError(Exception):
    """Carries the HTTP status + body so seeds can branch on gaps (404/403/...)."""

    def __init__(self, status: int, body: str, url: str):
        super().__init__(f"HTTP {status} for {url}: {body[:200]}")
        self.status = status
        self.body = body
        self.url = url


class V2Client:
    """appId-native Shepard REST client used by the feature-showcase seeds."""

    def __init__(self, host: str, api_key: str):
        self.root = _norm_root(host)
        self.v1 = f"{self.root}/shepard/api"
        self.v2 = f"{self.root}/v2"
        self.api_key = api_key

    # ── low-level request ─────────────────────────────────────────────────
    def _request(self, method: str, url: str, body: dict | None = None,
                 content_type: str = "application/json",
                 accept: str = "application/json") -> object:
        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {"X-API-KEY": self.api_key, "Accept": accept}
        if data is not None:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8")
                if not raw:
                    return None
                if accept.startswith("application/json") or accept == "application/json":
                    try:
                        return json.loads(raw)
                    except json.JSONDecodeError:
                        return raw
                return raw
        except urllib.error.HTTPError as e:
            raise HttpError(e.code, e.read().decode("utf-8", "replace"), url) from None

    # ── verbs ─────────────────────────────────────────────────────────────
    def get(self, path: str, accept: str = "application/json", v1: bool = False):
        base = self.v1 if v1 else self.v2
        return self._request("GET", f"{base}{path}", accept=accept)

    def post(self, path: str, body: dict, v1: bool = False):
        base = self.v1 if v1 else self.v2
        return self._request("POST", f"{base}{path}", body=body)

    def patch(self, path: str, body: dict, v1: bool = False):
        base = self.v1 if v1 else self.v2
        return self._request("PATCH", f"{base}{path}", body=body,
                             content_type="application/merge-patch+json")

    def delete(self, path: str, v1: bool = False):
        base = self.v1 if v1 else self.v2
        return self._request("DELETE", f"{base}{path}")

    # ── multipart file upload (FR1b singleton FileReference) ──────────────
    def upload_file(self, parent_data_object_app_id: str, name: str,
                    content: bytes, filename: str | None = None) -> dict:
        """Multipart ``POST /v2/files`` → a singleton FileReference (FR1b).

        Per the CLAUDE.md "one file → singleton FileReference" rule, a single
        file is minted via this multipart entry point, NOT via
        ``/v2/references?kind=file`` (that path rejects binary kinds with 400)
        and NOT as a FileBundleReference. Returns the created reference record
        (carries the minted ``appId``).
        """
        import os as _os

        boundary = "----shepardfeatseed" + _os.urandom(8).hex()
        fname = filename or name
        crlf = b"\r\n"
        parts = [
            ("--" + boundary).encode(),
            ('Content-Disposition: form-data; name="file"; filename="' + fname + '"').encode(),
            b"Content-Type: application/octet-stream",
            b"",
            content if isinstance(content, (bytes, bytearray)) else content.encode("utf-8"),
            ("--" + boundary + "--").encode(),
        ]
        data = crlf.join(parts) + crlf
        q = urllib.parse.urlencode({
            "parentDataObjectAppId": parent_data_object_app_id,
            "name": name,
        })
        url = f"{self.v2}/files?{q}"
        headers = {
            "X-API-KEY": self.api_key,
            "Accept": "application/json",
            "Content-Type": "multipart/form-data; boundary=" + boundary,
        }
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raise HttpError(e.code, e.read().decode("utf-8", "replace"), url) from None

    # ── domain helpers (all appId-native on the way out) ──────────────────
    def find_collection_by_name(self, name: str) -> dict | None:
        """Return the v1 Collection record (carries both numeric id AND appId)."""
        cols = self.get("/collections", v1=True)
        for c in cols or []:
            if c.get("name") == name:
                return c
        return None

    def reset_collection(self, name: str) -> bool:
        """Idempotent teardown: delete the named Collection if present.

        Returns True if a collection was deleted, False if none existed.
        Deleting a Collection cascades its DataObjects, references, and the
        semantic annotations attached to them.
        """
        existing = self.find_collection_by_name(name)
        if existing is None:
            return False
        self.delete(f"/collections/{existing['id']}", v1=True)
        return True

    def create_collection(self, name: str, description: str,
                          attributes: dict | None = None) -> dict:
        """Create a Collection via v1 (response carries the minted appId)."""
        return self.post("/collections", {
            "name": name,
            "description": description,
            "attributes": attributes or {},
        }, v1=True)

    def create_data_object(self, collection_app_id: str, name: str,
                           description: str = "", attributes: dict | None = None,
                           status: str | None = None,
                           typed_predecessors: list[dict] | None = None,
                           parent_app_id: str | None = None) -> dict:
        """Create a DataObject via the v2 appId-native endpoint.

        ``typed_predecessors`` is a list of
        ``{"predecessorAppId": <uuid>, "relationshipType": <str>}``.
        ``status`` is best-effort: the EN 9100 quality statuses (NCR_OPEN,
        ON_HOLD, REJECTED, CERTIFIED, CONCESSION_PENDING) are role-gated to
        ``quality-engineer`` and will 403 under plain API-key auth — callers
        should pass ``status`` only when they intend to catch that gap.
        """
        body: dict = {"name": name, "description": description,
                      "attributes": attributes or {}}
        if status is not None:
            body["status"] = status
        if typed_predecessors:
            body["typedPredecessors"] = typed_predecessors
        if parent_app_id is not None:
            body["parentAppId"] = parent_app_id
        return self.post(f"/collections/{collection_app_id}/data-objects", body)

    def patch_data_object(self, collection_app_id: str, do_app_id: str,
                          body: dict) -> dict:
        return self.patch(f"/collections/{collection_app_id}/data-objects/{do_app_id}", body)

    def patch_collection(self, collection_app_id: str, body: dict) -> dict:
        return self.patch(f"/collections/{collection_app_id}", body)

    def create_annotation(self, subject_app_id: str, subject_kind: str,
                          predicate_iri: str, *, object_literal: str | None = None,
                          object_iri: str | None = None,
                          vocabulary_id: str | None = None,
                          predicate_label: str | None = None) -> dict:
        """POST /v2/annotations — SEMA-V6 canonical :SemanticAnnotation write.

        Exactly one of ``object_literal`` / ``object_iri`` must be supplied.
        """
        body: dict = {
            "subjectAppId": subject_app_id,
            "subjectKind": subject_kind,
            "predicateIri": predicate_iri,
        }
        if object_literal is not None:
            body["objectLiteral"] = object_literal
        if object_iri is not None:
            body["objectIri"] = object_iri
        if vocabulary_id is not None:
            body["vocabularyId"] = vocabulary_id
        if predicate_label is not None:
            body["predicateLabel"] = predicate_label
        return self.post("/annotations", body)

    def list_annotations(self, subject_app_id: str) -> list:
        result = self.get(f"/annotations?subjectAppId={urllib.parse.quote(subject_app_id)}")
        return result if isinstance(result, list) else []

    def list_vocabularies(self) -> dict[str, str]:
        """Return {label -> vocabulary appId} for the bootstrapped vocabularies."""
        vocs = self.get("/semantic/vocabularies")
        out: dict[str, str] = {}
        for v in vocs or []:
            label = v.get("label") or v.get("namespaceUri") or v.get("appId")
            out[label] = v.get("appId")
        return out

    def sparql(self, query: str, repo: str = "internal") -> object:
        """GET /v2/semantic/{repo}/sparql — may raise HttpError on the n10s gap."""
        q = urllib.parse.quote(query)
        return self._request(
            "GET", f"{self.v2}/semantic/{repo}/sparql?query={q}",
            accept="application/sparql-results+json",
        )

    def users_me(self) -> dict:
        return self.get("/users/me")

    def frontend_collection_url(self, collection_app_id: str) -> str:
        host = self.root.replace("http://localhost:8080", "https://shepard.nuclide.systems")
        return f"{host}/collections/{collection_app_id}"

    def frontend_data_object_url(self, collection_app_id: str, do_app_id: str) -> str:
        host = self.root.replace("http://localhost:8080", "https://shepard.nuclide.systems")
        return f"{host}/collections/{collection_app_id}/dataobjects/{do_app_id}"


def build_arg_parser(description: str) -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=description)
    p.add_argument("--host", default=os.environ.get("BACKEND_URL", DEFAULT_HOST),
                   help="Shepard backend root or /shepard/api base URL.")
    p.add_argument("--apikey", default=os.environ.get("API_KEY"),
                   help="X-API-KEY value (or set API_KEY env / /tmp/reseed_apikey.txt).")
    p.add_argument("--reset", action="store_true",
                   help="Delete the existing feat-<slug> Collection before seeding.")
    return p


def client_from_args(args: argparse.Namespace) -> V2Client:
    api_key = args.apikey
    if not api_key and Path(DEFAULT_KEY_FILE).exists():
        api_key = Path(DEFAULT_KEY_FILE).read_text().strip()
    if not api_key:
        raise SystemExit(
            "No API key. Pass --apikey, set API_KEY, or place a key in "
            + DEFAULT_KEY_FILE
        )
    return V2Client(args.host, api_key)


def log(action: str, name: str, kind: str, ident: object = "") -> None:
    tail = f"  [{ident}]" if ident != "" else ""
    print(f"  {action:5s} {kind}: {name}{tail}", flush=True)


# ── ported from the MFFD set's _client variant (merge keep-ours reconciliation) ──

class ShepardError(RuntimeError):
    def __init__(self, status: int, body: str, url: str):
        super().__init__(f"HTTP {status} on {url}: {body[:300]}")
        self.status = status
        self.body = body
        self.url = url


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


