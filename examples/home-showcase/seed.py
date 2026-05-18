"""home-showcase — one-shot seed that provisions the Collection +
DataObjects + TimeseriesContainers used by the live MQTT collector.

Designed to be idempotent: running it twice does nothing harmful;
each "ensure_*" step looks the entity up by name first.

Three containers (per the user direction 2026-05-18):

  - solar-powerocean       — inverter telemetry (empty until the
                              PowerOcean bridge is wired up)
  - home-energy-appliances — zigbee2mqtt smart-plug power + energy
  - home-environment       — temperature / humidity / pressure / lux

The collector (collector.py) targets these container ids by name
after the seed has run."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass
from typing import Optional


# ---------------------------------------------------------------------------
# Constants

COLLECTION_NAME = "Home energy & environment (live)"
COLLECTION_DESCRIPTION = (
    "Live MQTT-ingested home telemetry — solar inverter, smart-plug energy, "
    "and indoor environment. Data flows in continuously through the home-showcase "
    "collector (see examples/home-showcase/collector.py). Distinct from the "
    "LUMEN hot-fire showcase, which uses synthetic deterministic data."
)

# (data-object-name, container-name, payload-description)
TARGETS = [
    ("Solar & battery", "solar-powerocean",
        "PowerOcean inverter — solar production, battery state, grid in/out. "
        "Empty until the PowerOcean integration is bridged into MQTT."),
    ("Smart plugs", "home-energy-appliances",
        "Per-appliance power (W) and energy (kWh) from zigbee2mqtt smart plugs."),
    ("Indoor environment", "home-environment",
        "Temperature, humidity, illuminance, and pressure across all rooms. "
        "Channels carry their measurement class in the `measurement` segment."),
]


@dataclass
class SeededEntities:
    collection_id: int
    data_object_ids: dict  # name -> id
    container_ids: dict  # name -> id


# ---------------------------------------------------------------------------
# Helpers — kept stdlib-only so the seeder image stays slim

def _log(action: str, name: str, kind: str = "", ident: object = "") -> None:
    print(f"{action:6} {name}/{kind} {ident}".rstrip())


def _import_client_or_die():
    try:
        from shepard_client import (  # type: ignore
            ApiClient, CollectionApi, Configuration, ContainerType,
            DataObjectApi, TimeseriesContainerApi, TimeseriesReferenceApi,
        )
    except Exception as e:  # pragma: no cover
        print(f"shepard_client import failed: {e}", file=sys.stderr)
        sys.exit(2)
    return (
        ApiClient, CollectionApi, Configuration, ContainerType,
        DataObjectApi, TimeseriesContainerApi, TimeseriesReferenceApi,
    )


def _fetch_collection_app_id(host: str, api_key: str, collection_id: int) -> Optional[str]:
    """Read the raw v1 JSON to extract `appId` — the upstream
    `shepard_client.Collection` typed model omits it."""
    try:
        import urllib.error
        import urllib.request
    except Exception:
        return None
    base = host.rstrip("/")
    if not base.endswith("/shepard/api"):
        base = base + "/shepard/api"
    url = f"{base}/collections/{collection_id}"
    req = urllib.request.Request(
        url,
        headers={
            "X-API-KEY": api_key,
            "apikey": api_key,
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body.get("appId")
    except Exception:
        return None


def _fetch_container_app_id(host: str, api_key: str, container_id: int) -> Optional[str]:
    """Mirror of `_fetch_collection_app_id` for TimeseriesContainers —
    the typed model omits appId here too."""
    try:
        import urllib.error
        import urllib.request
    except Exception:
        return None
    base = host.rstrip("/")
    if not base.endswith("/shepard/api"):
        base = base + "/shepard/api"
    url = f"{base}/timeseriesContainers/{container_id}"
    req = urllib.request.Request(
        url,
        headers={
            "X-API-KEY": api_key,
            "apikey": api_key,
            "Accept": "application/json",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body.get("appId")
    except Exception:
        return None


def _ensure_watches(host: str, api_key: str, coll_app_id: str, ts_api, container_names: list) -> None:
    """POST a :Watch from the home Collection to each TimeseriesContainer
    so the Collection page's WATCH1 panel shows the live containers.

    Uses direct urllib (the upstream shepard_client has no Watch API yet)
    against /v2/collections/{collectionAppId}/watched-containers. The
    endpoint is idempotent on (collection, container) — a duplicate POST
    returns the existing watch, not 409.

    Container appIds come from the TimeseriesContainerApi look-ups
    (already paginated through during ensure_timeseries_container)."""
    try:
        import urllib.error
        import urllib.request
    except Exception:
        _log("SKIP", "watches", "urllib unavailable")
        return

    v2_base = host.rstrip("/")
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]

    # Look up each container by name to get its appId.
    headers = {
        "X-API-KEY": api_key,
        "apikey": api_key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    for name in container_names:
        existing = _find_container_by_name(None, ts_api, name)
        if existing is None:
            _log("SKIP", f"watch-{name}", "container not found")
            continue
        # Same caveat as the Collection appId: the typed model omits
        # `appId`, so fall back to a raw v1 GET that returns the
        # full JSON shape including the appId.
        container_app_id = _fetch_container_app_id(host, api_key, existing.id)
        if not container_app_id:
            _log("SKIP", f"watch-{name}", "container has no appId")
            continue
        url = f"{v2_base}/v2/collections/{coll_app_id}/watched-containers"
        body = json.dumps({
            "containerKind": "TIMESERIES",
            "containerAppId": container_app_id,
        }).encode("utf-8")
        try:
            req = urllib.request.Request(url, data=body, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=5) as resp:
                _log("OK", f"watch-{name}", "Watch", container_app_id[:8])
        except urllib.error.HTTPError as e:
            _log("SKIP", f"watch-{name}", f"HTTP {e.code}")
        except Exception as exc:
            _log("SKIP", f"watch-{name}", f"error: {str(exc)[:60]}")


def _ensure_public(get_perms_fn, set_perms_fn, entity_id: int, label: str) -> None:
    """Flip an entity's permission-type to PUBLIC so every authenticated user
    (alice, bob, anyone in Keycloak) can read it. The seeder runs as
    admin and creates entities as private-to-admin by default — without
    this step, a freshly-deployed demo shows nothing to non-admin users
    and "demo confusion" sets in (user reports 2026-05-18).

    Soft-fail on permission errors: an entity left over from an earlier
    seed run by a different admin user can 403 the perms read. Log +
    skip rather than abort — the rest of the seed should still run."""
    try:
        from shepard_client import PermissionType  # type: ignore
    except Exception:
        _log("SKIP", f"public-{label}", "shepard_client.PermissionType unavailable")
        return
    try:
        perms = get_perms_fn(entity_id)
        if getattr(perms, "permission_type", None) == PermissionType.PUBLIC:
            return
        perms.permission_type = PermissionType.PUBLIC
        set_perms_fn(entity_id, perms)
        _log("OK", f"public-{label}", "PUBLIC", entity_id)
    except Exception as e:
        _log("SKIP", f"public-{label}", f"403 — stale ownership? ({str(e)[:60]})", entity_id)


# ---------------------------------------------------------------------------
# Idempotent provisioning steps

def _find_collection_by_name(coll_api, name):
    """Walk paginated /collections looking for `name`. Returns the
    collection object or None."""
    page = 0
    while True:
        try:
            collections = coll_api.get_all_collections(page=page, size=50)
        except Exception:
            return None
        items = list(collections) if collections is not None else []
        if not items:
            return None
        for c in items:
            if getattr(c, "name", None) == name:
                return c
        if len(items) < 50:
            return None
        page += 1
        if page > 50:  # safety
            return None


def _find_container_by_name(coll_api_or_client, ts_api, name):
    """ts_api.search_timeseries_containers exists in some client versions;
    fall back to get_all_timeseries_containers paginated."""
    try:
        for c in ts_api.get_all_timeseries_containers(page=0, size=200):
            if getattr(c, "name", None) == name:
                return c
    except Exception:
        pass
    return None


def _attrs_equal(existing, target: dict) -> bool:
    """`existing.attributes` may be a dict, a list of (key,value) records,
    or a list of pydantic Attribute objects depending on client version."""
    got = getattr(existing, "attributes", None) or {}
    if isinstance(got, list):
        flat = {}
        for entry in got:
            k = getattr(entry, "key", None) or (entry.get("key") if isinstance(entry, dict) else None)
            v = getattr(entry, "value", None) or (entry.get("value") if isinstance(entry, dict) else None)
            if k is not None:
                flat[k] = v
        got = flat
    return got == target


def ensure_collection(coll_api):
    """Idempotent. On re-run, if description/attributes have drifted from
    the seed source, reconcile them in place — so editing this file and
    re-running picks up the change without manual cleanup."""
    from shepard_client import Collection  # type: ignore
    target_attrs = {"source": "home-showcase", "live": "true"}
    existing = _find_collection_by_name(coll_api, COLLECTION_NAME)
    if existing is not None:
        drift = (
            getattr(existing, "description", None) != COLLECTION_DESCRIPTION
            or not _attrs_equal(existing, target_attrs)
        )
        if not drift:
            _log("SKIP", COLLECTION_NAME, "Collection", existing.id)
            return existing
        try:
            updated_body = Collection(
                name=COLLECTION_NAME,
                description=COLLECTION_DESCRIPTION,
                attributes=target_attrs,
            )
            coll_api.update_collection(collection_id=existing.id, collection=updated_body)
            _log("UPD", COLLECTION_NAME, "Collection", existing.id)
        except Exception as e:
            _log("WARN", COLLECTION_NAME, f"reconcile failed ({str(e)[:80]})", existing.id)
        return existing
    created = coll_api.create_collection(
        Collection(name=COLLECTION_NAME, description=COLLECTION_DESCRIPTION, attributes=target_attrs)
    )
    _log("OK", COLLECTION_NAME, "Collection", created.id)
    return created


def ensure_data_object(do_api, coll_id: int, name: str, description: str):
    from shepard_client import DataObject  # type: ignore
    target_attrs = {"source": "home-showcase"}
    existing_match = None
    try:
        existing = do_api.get_all_data_objects(collection_id=coll_id, page=0, size=200)
        for d in (existing or []):
            if getattr(d, "name", None) == name:
                existing_match = d
                break
    except Exception:
        pass
    if existing_match is not None:
        drift = (
            getattr(existing_match, "description", None) != description
            or not _attrs_equal(existing_match, target_attrs)
        )
        if not drift:
            _log("SKIP", name, "DataObject", existing_match.id)
            return existing_match
        try:
            do_api.update_data_object(
                collection_id=coll_id,
                data_object_id=existing_match.id,
                data_object=DataObject(name=name, description=description, attributes=target_attrs),
            )
            _log("UPD", name, "DataObject", existing_match.id)
        except Exception as e:
            _log("WARN", name, f"reconcile failed ({str(e)[:80]})", existing_match.id)
        return existing_match
    created = do_api.create_data_object(
        collection_id=coll_id,
        data_object=DataObject(name=name, description=description, attributes=target_attrs),
    )
    _log("OK", name, "DataObject", created.id)
    return created


def ensure_timeseries_container(ts_api, name: str, description: str):
    """Idempotent create. The upstream `/shepard/api` TimeseriesContainerApi
    does NOT expose an update method (containers are append-only by design —
    metadata mutation would race with concurrent ingest). So on re-run we
    only LOG drift; an operator who actually wants to refresh container
    metadata deletes-and-recreates manually."""
    from shepard_client import TimeseriesContainer  # type: ignore
    target_attrs = {"source": "home-showcase"}
    existing = _find_container_by_name(None, ts_api, name)
    if existing is not None:
        drift = (
            getattr(existing, "description", None) != description
            or not _attrs_equal(existing, target_attrs)
        )
        if drift:
            _log("NOTE", name, "TimeseriesContainer drift (no update API — recreate to refresh)", existing.id)
        else:
            _log("SKIP", name, "TimeseriesContainer", existing.id)
        return existing
    created = ts_api.create_timeseries_container(
        timeseries_container=TimeseriesContainer(
            name=name, description=description, attributes=target_attrs,
        ),
    )
    _log("OK", name, "TimeseriesContainer", created.id)
    return created


def ensure_timeseries_reference(tsr_api, coll_id: int, do_id: int, container_id: int, name: str):
    """A timeseries reference attaches a Container to a DataObject.
    Idempotent by name."""
    try:
        existing = tsr_api.get_all_timeseries_references(
            collection_id=coll_id, data_object_id=do_id
        )
        for r in (existing or []):
            if getattr(r, "name", None) == name:
                _log("SKIP", name, "TimeseriesReference", r.id)
                return r
    except Exception:
        pass

    from shepard_client import TimeseriesReference  # type: ignore
    # We use a placeholder open-ended range — actual ingest happens via the
    # collector adding TimeseriesDataPoint rows; the reference is purely a
    # graph edge.
    new = TimeseriesReference(
        name=name,
        start=0,
        end=10_000_000_000 * 1_000_000_000,  # nominally "open"
        timeseries=[],
        timeseries_container_id=container_id,
    )
    created = tsr_api.create_timeseries_reference(
        collection_id=coll_id,
        data_object_id=do_id,
        timeseries_reference=new,
    )
    _log("OK", name, "TimeseriesReference", created.id)
    return created


# ---------------------------------------------------------------------------
# Main

def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--host",
        default=os.environ.get("BACKEND_URL", "http://backend:8080/shepard/api"),
        help="shepard backend URL (root with /shepard/api)",
    )
    ap.add_argument(
        "--apikey",
        default=os.environ.get("SHEPARD_API_KEY"),
        help="API key for admin (consumed via apikey header)",
    )
    args = ap.parse_args(argv)
    if not args.apikey:
        ap.error("set --apikey or SHEPARD_API_KEY env var")

    (
        ApiClient, CollectionApi, Configuration, ContainerType,
        DataObjectApi, TimeseriesContainerApi, TimeseriesReferenceApi,
    ) = _import_client_or_die()

    cfg = Configuration(host=args.host)
    cfg.api_key["apikey"] = args.apikey
    client = ApiClient(cfg)

    coll_api = CollectionApi(client)
    do_api = DataObjectApi(client)
    ts_api = TimeseriesContainerApi(client)
    tsr_api = TimeseriesReferenceApi(client)

    coll = ensure_collection(coll_api)
    # Make the Collection readable by every authenticated user so
    # non-admin demo visitors (alice, bob) see the home Collection on
    # /collections. Mirrors LUMEN's `_ensure_public` pattern.
    _ensure_public(
        coll_api.get_collection_permissions,
        coll_api.edit_collection_permissions,
        coll.id,
        "collection",
    )

    # Pull the Collection's appId so we can wire WATCH1 watch links
    # below. The upstream shepard-client's `Collection` model doesn't
    # carry `appId` (it was generated before L2a shipped), so we hit
    # the raw v1 endpoint and parse the response — the appId IS in
    # the response body since L2a/L2b, just not in the typed model.
    coll_app_id = _fetch_collection_app_id(args.host, args.apikey, coll.id)

    data_objects = {}
    containers = {}
    for (do_name, container_name, description) in TARGETS:
        do = ensure_data_object(do_api, coll.id, do_name, description)
        ct = ensure_timeseries_container(ts_api, container_name, description)
        # DataObjects inherit Collection-level permissions in shepard's model
        # — no separate Permissions node — so making the Collection PUBLIC
        # above is sufficient for DO visibility. TimeseriesContainers ARE
        # separate root entities with their own Permissions node, so the
        # explicit flip below is the only way alice's
        # GET /timeseriesContainers/{id} stops returning 403.
        _ensure_public(
            ts_api.get_timeseries_permissions,
            ts_api.edit_timeseries_permissions,
            ct.id,
            f"tsc-{container_name}",
        )
        data_objects[do_name] = do.id
        containers[container_name] = ct.id
        # Skip creating an empty TimeseriesReference up-front — the upstream
        # API requires `timeseries: [...]` to have ≥1 channel and we don't
        # know the channel list before the collector ingests its first
        # message. The collector / WATCH1 watch-link surfaces the container
        # on the collection page even without a per-DataObject Reference.
        _log("DEFER", f"{container_name}-ref", "TimeseriesReference (created lazily by collector)")

    # WATCH1 — wire each TimeseriesContainer as a "watched container" on
    # the home Collection so the Collection's detail page surfaces the
    # live data flowing in. Idempotent on the (collection, container)
    # pair (server returns the existing watch, not 409). Requires the
    # backend's NeoConnector to know about the :Watch entity (added in
    # an earlier commit). Best-effort — failure here doesn't kill the
    # seed; the collector still flushes, just the Collection page won't
    # show the watch panel until this step succeeds on a re-run.
    if coll_app_id:
        _ensure_watches(args.host, args.apikey, coll_app_id, ts_api, list(containers.keys()))
    else:
        _log("SKIP", "watches", "no Collection appId — can't address /v2/collections/{appId}/watched-containers")

    # Dump the lookup table for the collector to consume.
    out = {
        "collection_id": coll.id,
        "data_object_ids": data_objects,
        "container_ids": containers,
    }
    out_path = os.environ.get("HOME_SHOWCASE_LOOKUP_PATH",
                              "/opt/home-showcase/seeded.json")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w") as f:
        json.dump(out, f, indent=2)
    _log("OK", "lookup table", "json", out_path)
    print(f"\nhome-showcase seed complete.\n"
          f"  Collection id: {coll.id}\n"
          f"  Container ids: {containers}\n"
          f"  Lookup: {out_path}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
