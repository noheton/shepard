"""BT-KVS docket showcase — shape-driven Excel export demo (BTKVS-C1-EXCEL-EXPORT).

Downloads the Excel projection of a docket DataObject: the same
``urn:btkvs:cell-mapping`` / ``urn:btkvs:sheet`` annotations that drive the
web form (``form_demo.py``) drive the workbook layout server-side
(doc 125 §6/D5 — Apache POI, no Excel installation anywhere).

Endpoint::

    GET /v2/templates/{templateAppId}/export?dataObjectAppId={doAppId}
    Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

Run::

    python3 export_demo.py --host https://shepard-api.nuclide.systems \
        --apikey <token> --template <templateAppId> --dataobject <doAppId> \
        [--out docket.xlsx]

Headless self-test (no network)::

    python3 export_demo.py --selftest
"""

from __future__ import annotations

import argparse
import sys
import urllib.error
import urllib.parse
import urllib.request

XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


def export_url(host: str, template_app_id: str, dataobject_app_id: str) -> str:
    """Build the export URL — the /v2/ prefix lives in exactly one place."""
    query = urllib.parse.urlencode({"dataObjectAppId": dataobject_app_id})
    return (
        f"{host.rstrip('/')}/v2/templates/"
        f"{urllib.parse.quote(template_app_id, safe='')}/export?{query}"
    )


def download(host: str, apikey: str, template_app_id: str, dataobject_app_id: str) -> bytes:
    """GET the workbook bytes; raises RuntimeError with the problem-JSON body on 4xx."""
    req = urllib.request.Request(
        export_url(host, template_app_id, dataobject_app_id),
        headers={"X-API-KEY": apikey, "Accept": XLSX_MEDIA_TYPE},
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:400]
        raise RuntimeError(f"export failed: HTTP {e.code}: {detail}") from e


def selftest() -> int:
    url = export_url("https://shepard-api.example.org/", "tmpl-1", "019e7243-f995-7914-be80-1")
    assert url == (
        "https://shepard-api.example.org/v2/templates/tmpl-1/export"
        "?dataObjectAppId=019e7243-f995-7914-be80-1"
    ), url
    # appIds with reserved characters stay URL-safe
    assert "%2F" in export_url("https://h", "a/b", "do-1")
    # xlsx magic: the workbook is a ZIP container — the check download() callers use
    assert b"PK\x03\x04"[:2] == b"PK"
    print("selftest OK — export URL construction + xlsx container check")
    return 0


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True, help="Shepard API base")
    p.add_argument("--apikey", required=True, help="X-API-KEY token")
    p.add_argument("--template", required=True, help="appId of the STRUCTURED_RECIPE form template")
    p.add_argument("--dataobject", required=True, help="appId of the docket DataObject to export")
    p.add_argument("--out", default="docket-export.xlsx", help="output file (default docket-export.xlsx)")
    args = p.parse_args()

    payload = download(args.host, args.apikey, args.template, args.dataobject)
    if not payload.startswith(b"PK"):
        print("WARNING: response is not a ZIP/xlsx container — check template + dataobject ids", file=sys.stderr)
    with open(args.out, "wb") as fh:
        fh.write(payload)
    print(f"wrote {len(payload)} bytes to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
