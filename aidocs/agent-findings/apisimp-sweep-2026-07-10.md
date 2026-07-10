---
stage: fragment
last-stage-change: 2026-07-10
---

# APISIMP sweep — fire-515 (2026-07-10)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings are
confined to the fork's development surface; the frozen `/shepard/api/` surface was not
examined. Previous sweep: fire-501 (apisimp-sweep-fire501-2026-07-09.md).

---

## §F1 — Dead tombstone classes ready for deletion

No new findings. `APISIMP-BUNDLE-TOMBSTONE-DELETE` and `APISIMP-WIKI-TOMBSTONE-DELETE`
(filed fire-500) are pending in the queue.

---

## §F2 — In-memory paging patterns

No new findings. All open in-memory paging rows from prior sweeps are either merged
or queued (APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING ✅, APISIMP-REFANNOT-IN-MEMORY-PAGING ✅,
APISIMP-TS-CONT-ANNOT-IN-MEMORY-PAGING ✅, APISIMP-SNAP-PINNED-IN-MEMORY-PAGING ✅).

---

## §F3 — Numeric ID leaks on the v2 wire

No new findings beyond the blocked `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE` (blocked on
TS-IDb/c, filed fire-437). No additional numeric-ID fields observed on active response
shapes.

---

## §F4 — Pagination inconsistencies

No new findings. All open pagination-envelope rows from prior sweeps are merged or queued.

---

## §F5 — Duplicate or redundant endpoints

No new findings. `APISIMP-PUBLICATIONS-KIND-410` (filed fire-500) and
`APISIMP-BUNDLE-TOMBSTONE-DELETE` cover the open cases.

---

## §F6 — @Parameter documentation gaps

No new findings. `APISIMP-BUNDLES-FILES-PAGESIZE-UNCLAMPED` (filed fire-500) remains
queued. `APISIMP-PAGEDFILES-SPRING-NAMING` (filed fire-500) remains queued.

---

## §F7 — Path/query param naming inconsistencies

### F7-1: `ContainersV2Rest` channel data endpoints use `{shepardId}` path param name

`backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`
lines 710, 733, 812, 830, 967

`GET /v2/containers/{appId}/channels/{shepardId}/data` and
`GET /v2/containers/{appId}/channels/{shepardId}/data-from` use `{shepardId}` as the
channel path parameter name — a carryover from the pre-appId era. The live-window
endpoint (`GET /v2/containers/{appId}/channels/live-window`) uses `?shepardId=` as a
query parameter for the same channel identity. The v2 canonical name for a channel UUID
is `channelAppId`, established by `APISIMP-ANOMALY-5TUPLE-ADD-UUID` (fire-511) when
`AnomalyDetectRequestIO` gained `channelAppId` as the new discriminator field.

When `TS-IDb/c` lands and `channelAppId` becomes the only channel identity, the path
template `{shepardId}` will be a stale misnomer. Callers building URLs at that point
will face a naming mismatch (every other channel endpoint says `channelAppId`; these
say `shepardId`). Filing now so the rename lands atomically with the TS-IDb/c gate
and we don't carry the inconsistency into the post-migration surface.

**Fix (after TS-IDb/c):** rename `@Path` templates from `/{shepardId}/data` to
`/{channelAppId}/data`; rename `@PathParam("shepardId")` to `@PathParam("channelAppId")`
on both data endpoints; rename `@QueryParam("shepardId")` on `getLiveWindow` to
`@QueryParam("channelAppId")`; tombstone old URL forms with 410 + `Location` header.

**Filed as**: APISIMP-CHANNEL-PATH-PARAM-RENAME (S, blocked on TS-IDb/c)

---

## Summary table

| ID | File | Size | Status |
|---|---|---|---|
| APISIMP-CHANNEL-PATH-PARAM-RENAME | `ContainersV2Rest.java:710,733,812,830,967` | S | blocked (TS-IDb/c) |

Categories §F1–§F6: no new findings this fire. Queue is healthy; all prior open rows
are either merged, in-flight as open PRs, or blocked on TS-IDb/c.
