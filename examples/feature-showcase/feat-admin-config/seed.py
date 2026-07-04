#!/usr/bin/env python3
"""feat-admin-config — A4: the generic /v2/admin/config/{feature} registry.

Proves V2CONV-A4 — one registry-driven admin-config surface that collapsed the
per-feature bespoke ``*ConfigRest`` classes:

  * ``GET   /v2/admin/config`` — list the registered feature keys.
  * ``GET   /v2/admin/config/{feature}`` — read the current config shape.
  * ``PATCH /v2/admin/config/{feature}`` — RFC 7396 merge-patch a field.

We exercise the ``ror`` feature (Research Organization Registry) because it is
the cleanest reversible target: it starts empty (``{}``), we PATCH a field, read
it back, then clear it again (PATCH ``null``) so re-runs leave no residue —
honouring the idempotency invariant and not mutating a feature another part of
the instance depends on.

A4 is admin-only; mutations land in :Activity via ProvenanceCaptureFilter
(PROV1a) automatically — this seed doesn't need to do anything for that.

NOTE: this seed has no feat-<slug> Collection — A4 is an instance-level admin
surface with no per-collection entity. It is therefore the one Core V2CONV seed
that does not create a Collection (documented in its README).

References:
  * RFC 7396 (JSON Merge Patch): https://www.rfc-editor.org/rfc/rfc7396
    (absent = leave alone, null = clear, value = replace — the exact semantics
    the PATCH applies)
  * ROR — Research Organization Registry: https://ror.org/

Run:
  /tmp/reseed-venv/bin/python examples/feature-showcase/feat-admin-config/seed.py \
      --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _shared import (  # noqa: E402
    build_parser,
    log,
    make_ctx,
    section,
    v2_get,
    v2_patch,
)

SLUG = "admin-config"

# ROR is a tri-state, reversible target. DLR's real ROR id is 04bwf3e34, but to
# stay synthetic we use a placeholder that still satisfies the [A-Za-z0-9]{1,9}
# validation, then clear it.
FEATURE = "ror"
DEMO_ROR_ID = "feat0a4cf"  # 9 chars, matches the backend's [A-Za-z0-9]{1,9} rule
DEMO_ORG = "feat-admin-config showcase org (synthetic)"


def main() -> None:
    args = build_parser(__doc__.splitlines()[0]).parse_args()
    ctx = make_ctx(args)

    section("LIST FEATURES — GET /v2/admin/config")
    status, rows, _ = v2_get(ctx, "/admin/config")
    if status != 200 or not isinstance(rows, list):
        log("FAIL", f"list features expected 200, got {status}: {str(rows)[:160]}")
        sys.exit(1)
    keys = [r.get("feature") for r in rows]
    log("OK", f"registered features: {', '.join(k for k in keys if k)}")
    if FEATURE not in keys:
        log("GAP", f"feature '{FEATURE}' not registered; available: {keys}")
        sys.exit(0)

    section(f"READ — GET /v2/admin/config/{FEATURE}")
    status, before, _ = v2_get(ctx, f"/admin/config/{FEATURE}")
    if status != 200:
        log("FAIL", f"read config expected 200, got {status}: {str(before)[:160]}")
        sys.exit(1)
    log("OK", f"before: {before}")

    section(f"PATCH — set rorId + organizationName (RFC 7396 merge-patch)")
    status, patched, _ = v2_patch(
        ctx,
        f"/admin/config/{FEATURE}",
        {"rorId": DEMO_ROR_ID, "organizationName": DEMO_ORG},
    )
    if status != 200:
        log("FAIL", f"patch expected 200, got {status}: {str(patched)[:160]}")
        sys.exit(1)
    log("OK", f"after patch: {patched}")
    ok = isinstance(patched, dict) and patched.get("rorId") == DEMO_ROR_ID
    log("OK" if ok else "WARN", f"rorId applied correctly: {ok}")
    if isinstance(patched, dict) and patched.get("rorUrl"):
        log("OK", f"derived rorUrl: {patched['rorUrl']}")

    section(f"READ BACK — GET /v2/admin/config/{FEATURE}")
    status, readback, _ = v2_get(ctx, f"/admin/config/{FEATURE}")
    persisted = isinstance(readback, dict) and readback.get("rorId") == DEMO_ROR_ID
    log("OK" if persisted else "WARN", f"read-back confirms persistence: {persisted} → {readback}")

    section("CLEAR — PATCH null to keep the seed idempotent (leave no residue)")
    status, cleared, _ = v2_patch(
        ctx,
        f"/admin/config/{FEATURE}",
        {"rorId": None, "organizationName": None},
    )
    if status == 200:
        log("OK", f"cleared (idempotent): {cleared}")
    else:
        log("WARN", f"clear expected 200, got {status}: {str(cleared)[:160]}")

    section("UNKNOWN FEATURE — GET /v2/admin/config/does-not-exist → expect 404")
    status, body, _ = v2_get(ctx, "/admin/config/does-not-exist")
    log("OK" if status == 404 else "WARN", f"unknown feature → {status} (expect 404 problem+json)")

    section("OUTCOME")
    log("DONE", f"feat-{SLUG}: A4 generic admin-config registry exercised on '{FEATURE}'")
    log("NOTE", "no feat-<slug> Collection — A4 is an instance-level admin surface")
    log("LINK", "https://shepard.nuclide.systems/admin (Admin hub → config tiles)")


if __name__ == "__main__":
    main()
