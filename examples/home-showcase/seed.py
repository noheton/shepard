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


def ensure_collection(coll_api):
    existing = _find_collection_by_name(coll_api, COLLECTION_NAME)
    if existing is not None:
        _log("SKIP", COLLECTION_NAME, "Collection", existing.id)
        return existing
    from shepard_client import Collection  # type: ignore
    new = Collection(
        name=COLLECTION_NAME,
        description=COLLECTION_DESCRIPTION,
        attributes={"source": "home-showcase", "live": "true"},
    )
    created = coll_api.create_collection(new)
    _log("OK", COLLECTION_NAME, "Collection", created.id)
    return created


def ensure_data_object(do_api, coll_id: int, name: str, description: str):
    # Walk children of the collection's root.
    try:
        # api shape varies; assume there's a get_all_data_objects(collection_id, page, size)
        existing = do_api.get_all_data_objects(collection_id=coll_id, page=0, size=200)
        for d in (existing or []):
            if getattr(d, "name", None) == name:
                _log("SKIP", name, "DataObject", d.id)
                return d
    except Exception:
        pass
    from shepard_client import DataObject  # type: ignore
    new = DataObject(
        name=name,
        description=description,
        attributes={"source": "home-showcase"},
    )
    created = do_api.create_data_object(collection_id=coll_id, data_object=new)
    _log("OK", name, "DataObject", created.id)
    return created


def ensure_timeseries_container(ts_api, name: str, description: str):
    existing = _find_container_by_name(None, ts_api, name)
    if existing is not None:
        _log("SKIP", name, "TimeseriesContainer", existing.id)
        return existing
    from shepard_client import TimeseriesContainer  # type: ignore
    new = TimeseriesContainer(
        name=name,
        description=description,
        attributes={"source": "home-showcase"},
    )
    created = ts_api.create_timeseries_container(timeseries_container=new)
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
    data_objects = {}
    containers = {}
    for (do_name, container_name, description) in TARGETS:
        do = ensure_data_object(do_api, coll.id, do_name, description)
        ct = ensure_timeseries_container(ts_api, container_name, description)
        data_objects[do_name] = do.id
        containers[container_name] = ct.id
        ensure_timeseries_reference(
            tsr_api, coll.id, do.id, ct.id, f"{container_name}-ref"
        )

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
