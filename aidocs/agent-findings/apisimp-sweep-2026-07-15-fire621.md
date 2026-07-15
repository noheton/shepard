---
stage: fragment
last-stage-change: 2026-07-15
---

# APISIMP Sweep — fire-621 (2026-07-15)

Scan of `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java`,
`*IO.java`, and SPI handler Javadoc for residual naming-inconsistency violations
after the queue was exhausted (APISIMP-TSREF-CONTAINER-ID-DEPRECATE merged as PR #2587).

Categories checked:

- Raw-epoch `long`/`Long` timestamp/duration fields on the v2 wire → ISO 8601 (all clean)
- `@PathParam`/`@QueryParam` naming inconsistencies (`appId` vs. premature `shepardId` rename)
- `?size=` pagination param inconsistency (all clean)
- `@Parameter` Javadoc gaps (all clean)
- Throw-from-filter anti-patterns (all clean)

Conducted by background agent (`a16f2d01579aaa2ab`) with findings integrated below.

## Scope exclusions

- `/shepard/api/` v1 wire is frozen — changes to v1 IO types are out of scope.
- `appId → shepardId` coordinated rename (task #123 / `feedback_appid_to_shepardid.md`) is a single-pass future operation — this row targets only the *premature* ad-hoc rename that is inconsistent even within v2 (i.e. `channelShepardId` while the rest of v2 still uses `channelAppId`).
- APISIMP-TSCHANNEL-CONTAINER-ID-WIRE, APISIMP-PERM-AUDIT-NEO4J-ID, APISIMP-DQR-ORPHAN, APISIMP-LEDGER-ANCHOR-ORPHAN: blocked on L2e / operator decision — confirmed still blocked, not re-filed.

## Verified clean before this sweep

All v2 IO `long`/`Long` fields: byte/size/count fields only — confirmed clean by fire-620 sweep. No new raw-epoch longs introduced.

---

## Finding 1 — APISIMP-CHANNEL-SHEPARDID-PREMATURE-RENAME (size: M) — **QUEUED**

- **Files:**
  - `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`
  - `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/io/BulkChannelDataRequestIO.java`
  - `backend/src/main/java/de/dlr/shepard/v2/containers/spi/ContainerKindHandler.java`

- **Problem:** `ContainersV2Rest.java` uses `{channelShepardId}` as the path segment for timeseries channel identity at lines 709, 733, 829, 847, 1022, 1039, 1064, 1080, 1102, 1116, and `@QueryParam("shepardId")` at line 984. `BulkChannelDataRequestIO.java` uses `List<@NotNull UUID> shepardIds` (line 40) and a `@Schema(description = "Channel shepardIds...")` (line 36). `ContainerKindHandler.java` SPI Javadoc uses `channelShepardId` param names at lines 584, 590, 599, 604, 613, 618.

  This is an ad-hoc premature rename: CLAUDE.md explicitly states "do not rename individual fields ahead of the coordinated appId→shepardId pass." The coordinated pass (task #123) has not landed. Meanwhile `ReferenceActionsRest.java` (lines 94, 105, 118) uses `channelAppId` for the same concept — creating a within-v2 inconsistency that confuses callers.

- **Wire impact:** Path segments `{channelShepardId}` are part of the public v2 URL shape:
  - `GET /v2/containers/{appId}/channels/{channelShepardId}/data`
  - `POST /v2/containers/{appId}/channels/{channelShepardId}/data/ingest`
  - `GET /v2/containers/{appId}/channels/{channelShepardId}/annotations`
  - `POST /v2/containers/{appId}/channels/{channelShepardId}/annotations`
  - `DELETE /v2/containers/{appId}/channels/{channelShepardId}/annotations/{annotationAppId}`
  - `@QueryParam("shepardId")` on `GET /v2/containers/{appId}/channels`
  - `BulkChannelDataRequestIO.shepardIds` JSON key in `POST /v2/containers/{appId}/channels/data`

  All should use `channelAppId` / `channelAppIds` consistently.

- **Fix:**
  1. In `ContainersV2Rest.java`: rename all `{channelShepardId}` path segments → `{channelAppId}` (10 `@Path` + `@PathParam` occurrences). Rename `@QueryParam("shepardId")` → `@QueryParam("channelAppId")` (line 984). Update the four `@Parameter(description = "... shepardId ...")` descriptions at lines 981–983, 968.
  2. In `BulkChannelDataRequestIO.java`: rename field `shepardIds` → `channelAppIds` (line 40) and update `@Schema(description = ...)` at line 36. Update the record component getter (`shepardIds()` → `channelAppIds()`).
  3. In `ContainerKindHandler.java`: rename Javadoc `@param channelShepardId` → `@param channelAppId` at lines 584, 590, 599, 604, 613, 618. Rename method param names in the default/interface methods (lines 590, 604, 618) to match.
  4. Update any callers of the SPI methods in in-tree plugin modules that pass `channelShepardId` by position — check `TimeseriesContainerKindHandler.java` and other implementing classes.
  5. Update `backend-client/.openapi-source/openapi.json` path segments to match the new param names.

- **Backlog row:** `APISIMP-CHANNEL-SHEPARDID-PREMATURE-RENAME` (queued for fire-622).

---

## Campaign status after fire-621

| Row | Status |
|---|---|
| `APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS` | ✅ merged PR #2586 (fire-621) |
| `APISIMP-TSREF-LASTSCOREDAT-MS-TO-ISO` | ✅ merged PR #2586 (fire-621) |
| `APISIMP-TSREF-CONTAINER-ID-DEPRECATE` | ✅ merged PR #2587 (fire-621) |
| `APISIMP-CHANNEL-SHEPARDID-PREMATURE-RENAME` | 🔲 queued (fire-622) |
| `APISIMP-PERM-AUDIT-NEO4J-ID` | blocked on L2e |
| `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE` | blocked on TS-IDb/c |
| `APISIMP-DQR-ORPHAN` | blocked on operator decision |
| `APISIMP-LEDGER-ANCHOR-ORPHAN` | blocked on operator decision |

All v2 IO `Long`/`long` fields confirmed clean. All `@PathParam` and `@QueryParam` names confirmed consistent except the finding above.
