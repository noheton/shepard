"""Build-time slicer that emits the v1-shelf OpenAPI document.

CG1b — the OpenAPI Generator legacy clients (`clients/{java,python,typescript}`)
generate from the upstream-compatible `/shepard/api/...` surface only. The
backend exposes the same view at runtime as `GET /shepard/doc/openapi/v1.json`
(per P4c, `aidocs/16`), but the CI client-build pipeline reads the on-disk
`backend/target/openapi/openapi.json` produced by smallrye-openapi at
`mvn package` time — there is no live backend to call at that point.

This script is the build-time mirror of `V1OpenApiFilter` (Java, in
`backend/src/main/java/de/dlr/shepard/common/openapi/`). It takes the combined
`openapi.json`, drops every path that is on the `/v2/` shelf or is a platform
path, and writes the result. Operation-id-keyed `paths` entries are the only
v1-vs-v2 differentiator we care about — components (schemas) stay intact since
v1 and v2 share request/response models.

Path-membership semantics mirror `OpenApiShelfMembership.isV1Path` 1:1:

  - platform paths (`/healthz`, `/openapi`, `/openapi.json`, `/openapi.yaml`,
    `/swagger-ui`, `/metrics`) are dropped
  - `/v2/...` paths are dropped
  - everything else (including the upstream-stripped `/collections`,
    `/users/me`, ... shape that `ApiPathFilter` produces) is v1 — kept

Usage:

    python3 scripts/shepard_scripts/scripts/slice_openapi_v1.py \\
        backend/target/openapi/openapi.json \\
        backend/target/openapi/openapi_v1.json
"""

import json
import sys
from collections.abc import Iterable

# Mirrors OpenApiShelfMembership.PLATFORM_PREFIXES.
_PLATFORM_PREFIXES: tuple[str, ...] = ("/healthz", "/openapi", "/swagger-ui", "/metrics")

# Mirrors OpenApiShelfMembership.PLATFORM_DOTTED — paths that don't follow
# the prefix-plus-slash shape but are still platform-internal.
_PLATFORM_DOTTED: tuple[str, ...] = ("/openapi.json", "/openapi.yaml", "/openapi.yml")

_V2_PREFIX = "/v2"


def _matches_prefix(path: str, prefix: str) -> bool:
    """True when `path` equals `prefix` or starts with `prefix + '/'`.

    Mirrors `OpenApiShelfMembership.matchesPrefix` — guards against
    misclassifying e.g. `/healthzy` as a platform path because of the
    `/healthz` prefix.
    """
    return path == prefix or path.startswith(prefix + "/")


def _is_platform_path(path: str) -> bool:
    if any(_matches_prefix(path, prefix) for prefix in _PLATFORM_PREFIXES):
        return True
    return path in _PLATFORM_DOTTED


def is_v1_path(path: str) -> bool:
    """A path is v1 iff it is not platform and not `/v2/`-prefixed."""
    if not path:
        return False
    if _is_platform_path(path):
        return False
    if _matches_prefix(path, _V2_PREFIX):
        return False
    return True


def _filter_paths(paths: dict[str, object]) -> dict[str, object]:
    return {p: item for p, item in paths.items() if is_v1_path(p)}


def slice_v1(spec: dict) -> dict:
    """Return a deep-enough copy of `spec` with non-v1 paths removed.

    The OpenAPI Generator templates we use (java / python / typescript-fetch)
    key off `paths` to generate API classes; `components.schemas` is shared
    across v1 and v2 (the IO records are the same), so we leave components,
    info, servers, security, tags, etc. untouched.
    """
    sliced = dict(spec)
    paths = spec.get("paths", {}) or {}
    sliced["paths"] = _filter_paths(paths)
    return sliced


def _validate_no_v2_remaining(sliced: dict) -> None:
    """Belt-and-braces: assert no `/v2/...` paths leaked through."""
    leaked = [p for p in (sliced.get("paths") or {}) if p.startswith(_V2_PREFIX)]
    if leaked:
        raise RuntimeError(
            f"slice_openapi_v1: {len(leaked)} /v2/ paths leaked into v1 output: "
            f"{leaked[:3]}{'...' if len(leaked) > 3 else ''}"
        )


def main(input_spec: str, output_spec: str) -> None:
    with open(input_spec) as fp:
        spec = json.load(fp)

    sliced = slice_v1(spec)
    _validate_no_v2_remaining(sliced)

    before = len((spec.get("paths") or {}))
    after = len((sliced.get("paths") or {}))
    print(
        f"slice_openapi_v1: {before} -> {after} paths "
        f"({before - after} dropped: /v2/ + platform)",
        file=sys.stderr,
    )

    with open(output_spec, "w") as fp:
        json.dump(sliced, fp, indent=2)


def _cli(argv: Iterable[str]) -> int:
    args = list(argv)
    if len(args) != 2:
        print(
            "Usage: slice_openapi_v1.py <input-openapi.json> <output-openapi_v1.json>",
            file=sys.stderr,
        )
        return 1
    main(args[0], args[1])
    return 0


if __name__ == "__main__":
    sys.exit(_cli(sys.argv[1:]))
