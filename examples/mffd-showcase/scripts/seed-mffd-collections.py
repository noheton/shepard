#!/usr/bin/env python3
"""
seed-mffd-collections.py — idempotent bootstrap of the MFFD Project +
the 5 process-step Collections + the per-team UserGroups.

Per aidocs/integrations/119-mffd-collection-layout.md §8 step 1 and
aidocs/integrations/121-project-and-subcollections.md §8.

Shape:

    mffd-project                       (Collection carrying urn:shepard:project = "true"
                                        + urn:shepard:programme = "Clean Aviation JU"
                                        + urn:shepard:programme = "DLR Project Line 4")
      │
      ├── mffd-afp-tapelaying          (urn:shepard:partOf = mffd-project.appId)
      ├── mffd-bridge-welding          (urn:shepard:partOf = mffd-project.appId)
      ├── mffd-spot-welding            (urn:shepard:partOf = mffd-project.appId)
      ├── mffd-ndt-thermography        (urn:shepard:partOf = mffd-project.appId)
      └── mffd-cell                    (urn:shepard:partOf = mffd-project.appId)

UserGroups (per 119 §1):
    mffd-afp-team, mffd-welding-team, mffd-ndt-team, mffd-cell-team

Idempotency:
- Each Collection is matched by display name. If a Collection with the
  expected name already exists, the script keeps it and only patches /
  re-asserts the annotations.
- Annotations are matched on (subjectAppId, propertyIRI, valueName) —
  duplicates are skipped via a GET-before-POST step.
- UserGroups are matched by name; existing ones are kept.

Run:
    SHEPARD_HOST=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
        python3 seed-mffd-collections.py --dry-run

    SHEPARD_HOST=https://shepard.nuclide.systems \\
    SHEPARD_API_KEY=<your-key> \\
        python3 seed-mffd-collections.py

Output is verbose by default — every decision (create vs skip vs match)
is printed.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass
from typing import Optional

try:
    import requests
except ImportError:
    sys.stderr.write(
        "ERROR: this script needs the `requests` library.\n"
        "       pip install requests\n"
    )
    sys.exit(2)


# ── domain constants ────────────────────────────────────────────────────────

PROJECT_COLLECTION = {
    "name": "MFFD Upper Shell — Project",
    "description": (
        "MFFD upper-shell project umbrella. Bundles five non-exclusive "
        "process-step Collections (AFP tapelaying, bridge welding, spot "
        "welding, NDT thermography, cell). Carries the urn:shepard:project "
        "role marker and two programme labels (Clean Aviation JU + DLR "
        "Project Line 4)."
    ),
}

STEP_COLLECTIONS = [
    {
        "name": "mffd-afp-tapelaying",
        "description": "AFP layup process step — ~8 251 track DataObjects from cube3 Coll 48297.",
        "owner_group": "mffd-afp-team",
    },
    {
        "name": "mffd-bridge-welding",
        "description": "Bridge welding process step — ~13 AF × N executions.",
        "owner_group": "mffd-welding-team",
    },
    {
        "name": "mffd-spot-welding",
        "description": "Spot welding process step — 21 svdx + paired CSVs.",
        "owner_group": "mffd-welding-team",
    },
    {
        "name": "mffd-ndt-thermography",
        "description": "NDT thermography step — 707 process + 37 reference OTvis frames.",
        "owner_group": "mffd-ndt-team",
    },
    {
        "name": "mffd-cell",
        "description": "Manufacturing cell — MFZ.rdk + KR210 URDF + KRL trajectories.",
        "owner_group": "mffd-cell-team",
    },
]

USER_GROUPS = [
    "mffd-afp-team",
    "mffd-welding-team",
    "mffd-ndt-team",
    "mffd-cell-team",
]

PROGRAMMES = [
    "Clean Aviation JU",
    "DLR Project Line 4",
]

PRED_PROJECT = "urn:shepard:project"
PRED_PART_OF = "urn:shepard:partOf"
PRED_PROGRAMME = "urn:shepard:programme"


# ── HTTP helpers ────────────────────────────────────────────────────────────

@dataclass
class Client:
    host: str
    api_key: str
    dry_run: bool = False
    session: Optional[requests.Session] = None

    def __post_init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "X-API-KEY": self.api_key,
            "Accept": "application/json",
            "Content-Type": "application/json",
        })

    # ── low-level
    def _url(self, path: str) -> str:
        return f"{self.host.rstrip('/')}{path}"

    def _request(self, method: str, path: str, **kwargs):
        if self.dry_run and method in {"POST", "PUT", "PATCH", "DELETE"}:
            log(f"  [dry-run] {method} {path}")
            return None
        url = self._url(path)
        try:
            resp = self.session.request(method, url, timeout=60, **kwargs)
        except requests.RequestException as e:
            die(f"HTTP error on {method} {path}: {e}")
        return resp

    # ── collections (v1 + v2)
    def list_collections_v1(self):
        # v1 surface — the only one with a list endpoint as of writing.
        # /shepard/api/collections is paginated; the seed payload size is
        # tiny so the default first-page is sufficient on a fresh instance,
        # but we paginate defensively when the count grows.
        all_rows = []
        page = 0
        size = 50
        while True:
            resp = self._request(
                "GET",
                f"/shepard/api/collections?page={page}&size={size}",
            )
            if resp is None or resp.status_code >= 400:
                if resp is not None:
                    log(f"  WARN: list collections returned {resp.status_code}")
                break
            rows = resp.json()
            if not rows:
                break
            all_rows.extend(rows)
            if len(rows) < size:
                break
            page += 1
        return all_rows

    def create_collection_v1(self, name: str, description: str):
        body = {"name": name, "description": description, "attributes": {}}
        resp = self._request("POST", "/shepard/api/collections", data=json.dumps(body))
        if resp is None:
            return None
        if resp.status_code not in (200, 201):
            die(f"create Collection {name!r} failed: {resp.status_code} {resp.text[:200]}")
        return resp.json()

    def get_collection_v2(self, app_id: str):
        resp = self._request("GET", f"/v2/collections/{app_id}")
        if resp is None or resp.status_code != 200:
            return None
        return resp.json()

    # ── semantic annotations (v2)
    def list_annotations(self, subject_app_id: str, predicate_iri: Optional[str] = None):
        path = f"/v2/annotations?subjectAppId={subject_app_id}&subjectKind=Collection&pageSize=200"
        if predicate_iri:
            from urllib.parse import quote
            path += f"&predicateIri={quote(predicate_iri)}"
        resp = self._request("GET", path)
        if resp is None or resp.status_code != 200:
            return []
        return resp.json()

    def create_annotation(self, subject_app_id: str, predicate_iri: str, literal: str):
        body = {
            "subjectAppId": subject_app_id,
            "subjectKind": "Collection",
            "predicateIri": predicate_iri,
            "objectLiteral": literal,
            "sourceMode": "human",
        }
        resp = self._request("POST", "/v2/annotations", data=json.dumps(body))
        if resp is None:
            return None
        if resp.status_code not in (200, 201):
            die(
                f"create annotation ({predicate_iri} = {literal!r}) on {subject_app_id} "
                f"failed: {resp.status_code} {resp.text[:200]}"
            )
        return resp.json()

    # ── user groups (v1)
    def list_user_groups(self):
        resp = self._request("GET", "/shepard/api/userGroups?page=0&size=200")
        if resp is None or resp.status_code != 200:
            return []
        return resp.json()

    def create_user_group(self, name: str):
        # `usernames` is required by the v1 CreateUserGroup validator (must not
        # be null). Seed the group empty — operators add members later via the
        # admin UI / API. The creating user is implicitly the owner.
        body = {
            "name": name,
            "description": f"MFFD {name} (seeded by seed-mffd-collections.py)",
            "usernames": [],
        }
        resp = self._request("POST", "/shepard/api/userGroups", data=json.dumps(body))
        if resp is None:
            return None
        if resp.status_code not in (200, 201):
            log(f"  WARN: create UserGroup {name!r} returned {resp.status_code} {resp.text[:200]}")
            return None
        return resp.json()


# ── orchestration ───────────────────────────────────────────────────────────

def log(msg: str) -> None:
    sys.stdout.write(msg + "\n")
    sys.stdout.flush()


def die(msg: str) -> None:
    sys.stderr.write(f"FATAL: {msg}\n")
    sys.exit(1)


def find_collection_by_name(collections, name: str):
    """Find a Collection by exact display-name match."""
    for c in collections:
        if c.get("name") == name:
            return c
    return None


def ensure_annotation(client: Client, subject_app_id: str, predicate: str, value: str):
    """Idempotent annotation seed — skip if already present."""
    existing = client.list_annotations(subject_app_id, predicate)
    for ann in existing:
        # Match on the wire shape — propertyIRI + valueName.
        if (ann.get("predicateIri") or ann.get("propertyIRI")) == predicate:
            object_literal = ann.get("objectLiteral") or ann.get("valueName")
            if object_literal == value:
                log(f"    SKIP   {predicate} = {value!r} — already present")
                return ann
    log(f"    CREATE {predicate} = {value!r}")
    return client.create_annotation(subject_app_id, predicate, value)


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed the MFFD Project + step Collections + UserGroups.")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan; make no writes.")
    parser.add_argument(
        "--host",
        default=os.environ.get("SHEPARD_HOST"),
        help="Shepard host URL (env SHEPARD_HOST).",
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("SHEPARD_API_KEY"),
        help="Shepard API key (env SHEPARD_API_KEY).",
    )
    args = parser.parse_args()

    if not args.host:
        die("SHEPARD_HOST is not set (env or --host)")
    if not args.api_key:
        die("SHEPARD_API_KEY is not set (env or --api-key)")

    client = Client(host=args.host, api_key=args.api_key, dry_run=args.dry_run)

    log(f"shepard host : {args.host}")
    log(f"dry-run      : {args.dry_run}")
    log("")

    # ── Step 0: UserGroups ──────────────────────────────────────────────────
    log("== Step 0 — UserGroups")
    existing_groups = {ug.get("name"): ug for ug in client.list_user_groups()}
    for ug_name in USER_GROUPS:
        if ug_name in existing_groups:
            log(f"  SKIP    {ug_name} — already present")
            continue
        log(f"  CREATE  {ug_name}")
        client.create_user_group(ug_name)
    log("")

    # ── Step 1: Project Collection ──────────────────────────────────────────
    log("== Step 1 — MFFD Project Collection")
    all_collections = client.list_collections_v1()
    project = find_collection_by_name(all_collections, PROJECT_COLLECTION["name"])
    if project:
        log(f"  SKIP    create {PROJECT_COLLECTION['name']!r} — already present "
            f"(appId={project.get('appId')}, id={project.get('id')})")
    else:
        log(f"  CREATE  Collection {PROJECT_COLLECTION['name']!r}")
        project = client.create_collection_v1(
            PROJECT_COLLECTION["name"], PROJECT_COLLECTION["description"]
        )
        if project is None and not args.dry_run:
            die("Project Collection create returned no body")
        if args.dry_run:
            # Dry-run: synthesize a placeholder appId so subsequent
            # annotation prints make sense.
            project = {"appId": "<dry-run-project-appId>", "id": 0,
                       "name": PROJECT_COLLECTION["name"]}
        else:
            time.sleep(0.5)  # let the Collection settle in indices

    project_app_id = project["appId"]
    log(f"    project appId = {project_app_id}")
    log("")

    # ── Step 2: Project role marker + programmes ────────────────────────────
    log("== Step 2 — Project role marker + programmes")
    if args.dry_run:
        log(f"    [dry-run] would mark {project_app_id} as a Project + add programmes")
    else:
        ensure_annotation(client, project_app_id, PRED_PROJECT, "true")
        for programme in PROGRAMMES:
            ensure_annotation(client, project_app_id, PRED_PROGRAMME, programme)
    log("")

    # ── Step 3: Step Collections + partOf annotations ───────────────────────
    log("== Step 3 — Step Collections + partOf annotations")
    # Refresh the list so we see the just-created Project.
    if not args.dry_run:
        all_collections = client.list_collections_v1()

    for step in STEP_COLLECTIONS:
        existing = find_collection_by_name(all_collections, step["name"])
        if existing:
            log(f"  SKIP    create {step['name']!r} — already present "
                f"(appId={existing.get('appId')}, id={existing.get('id')})")
            step_collection = existing
        else:
            log(f"  CREATE  Collection {step['name']!r}")
            step_collection = client.create_collection_v1(step["name"], step["description"])
            if step_collection is None and not args.dry_run:
                die(f"step Collection {step['name']!r} create returned no body")
            if args.dry_run:
                step_collection = {"appId": f"<dry-run-{step['name']}-appId>", "id": 0,
                                   "name": step["name"]}

        step_app_id = step_collection["appId"]
        log(f"    {step['name']} appId = {step_app_id}")

        if args.dry_run:
            log(f"    [dry-run] would set urn:shepard:partOf = {project_app_id} on {step_app_id}")
        else:
            ensure_annotation(client, step_app_id, PRED_PART_OF, project_app_id)
    log("")

    log("== Done.")
    log("")
    log("Verify with:")
    log(f"  curl -H 'X-API-KEY: <key>' {args.host}/v2/projects | jq")
    log(f"  curl -H 'X-API-KEY: <key>' {args.host}/v2/projects/{project_app_id} | jq")
    log(f"  curl -H 'X-API-KEY: <key>' {args.host}/v2/projects/{project_app_id}/sub-collections | jq")
    return 0


if __name__ == "__main__":
    sys.exit(main())
