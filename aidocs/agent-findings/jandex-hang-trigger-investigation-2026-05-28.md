---
stage: feature-defined
last-stage-change: 2026-05-28
audience: contributors, operators
---

# Jandex hang — trigger-class hunt (2026-05-28)

Follow-up to [`backend-jandex-hang-investigation-2026-05-28.md`](backend-jandex-hang-investigation-2026-05-28.md).
That doc captured the stack trace and ranked suspects S1–S4. This doc narrows the
search to a specific class added between commit `6b3550d30` (last-known-good
2026-05-26 18:59) and current HEAD (`33a25b29e`).

## Diff window measurements

- Commits between `6b3550d30..HEAD`: **277**
- Java files changed (backend + plugins): **213**
- Files with new `implements` clauses (added `+` lines): **18**

## Indexed-dependency baseline (`backend/src/main/resources/application.properties`)

Existing `quarkus.index-dependency.*` entries cover every `de.dlr.shepard.plugins.*`
artifact that backend depends on: `unhide, kip, minter-local, minter-datacite,
minter-epic, spatiotemporal, hdf5, git, file-s3, aas, video, ai, wiki-writer,
importer, analytics-ts, v1-compat`. There is **no entry for `shepard-admin`
(the CLI module)** — see "Top angle" below.

## Files with new implements clauses (full list)

| # | File | Class | Parent interface | Indexed? | Confidence |
|---|------|-------|------------------|----------|------------|
| 1 | `plugins/video/src/main/java/de/dlr/shepard/plugins/video/cli/VideoCommand.java` | `VideoCommand` | `java.lang.Runnable` (with `@picocli.CommandLine.Command` annotation) | Runnable: JDK (Quarkus core); picocli: NOT indexed (provided scope) | **MEDIUM-HIGH** |
| 2 | `plugins/video/src/main/java/de/dlr/shepard/plugins/video/cli/VideoAdminCliCommandProvider.java` | `VideoAdminCliCommandProvider` | `de.dlr.shepard.cli.plugin.AdminCliCommandProvider` | `shepard-admin` NOT a backend dep, NOT in `quarkus.index-dependency.*` | **MEDIUM-HIGH** |
| 3 | `plugins/video/src/main/java/de/dlr/shepard/plugins/video/entities/VideoConfig.java` | `VideoConfig` | `de.dlr.shepard.plugin.HasAppId` | Yes (backend's own classes) | LOW |
| 4 | `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/entities/AasConfig.java` | `AasConfig` | `HasAppId` | Yes | LOW |
| 5 | `backend/src/main/java/de/dlr/shepard/v2/admin/instance/entities/InstanceRegistry.java` | `InstanceRegistry` | `HasAppId` | Yes (own backend) | LOW |
| 6 | `plugins/hdf5/src/main/java/de/dlr/shepard/data/hdf/entities/HdfReference.java` | `HdfReference` | `HasAppId` (also `extends BasicReference`) | Yes | LOW |
| 7 | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/plugins/spatiotemporal/SpatiotemporalPluginManifest.java` | `SpatiotemporalPluginManifest` | `de.dlr.shepard.plugin.PluginManifest` | Yes | LOW-MEDIUM (interface mutated, see below) |
| 8 | `plugins/spatiotemporal/.../SpatiotemporalPayloadKind.java` | `SpatiotemporalPayloadKind` | `de.dlr.shepard.spi.payload.PayloadKind` | Yes | LOW |
| 9 | `plugins/spatiotemporal/.../GeoTimeVocabularyProvider.java` | `GeoTimeVocabularyProvider` | `SemanticVocabularyProvider` | Yes | LOW |
| 10 | `plugins/spatiotemporal/.../SpatialDataReferenceService.java` | `SpatialDataReferenceService` | `IReferenceService<SpatialDataReference, SpatialDataReferenceIO>` (generic) | Yes | LOW-MEDIUM (generic bounds) |
| 11–16 | `backend/.../context/export/{Basic,File,StructuredData,Timeseries,URI}ReferenceExportHandler.java` | five `*ExportHandler` classes | `PayloadExportHandler` (internal SPI added this window) | Yes (own backend) | LOW |
| 17 | `backend/.../auth/permission/services/GraphPolicyDecisionPoint.java` | `GraphPolicyDecisionPoint` | `PolicyDecisionPoint` (new interface this window) | Yes | LOW |
| 18 | `plugins/fileformat-thermography/.../OTvisParser.java` | `OTvisParser` | `FileParserPlugin` (in same plugin module) | **Module STANDALONE, NOT in aggregator, NOT a backend dep** — should not reach Jandex | EXCLUDED |

## Top angle (the strongest concrete hypothesis)

**`AdminCliCommandProvider` is an unresolved-from-backend SPI interface.**

- The interface lives in `cli/src/main/java/de/dlr/shepard/cli/plugin/AdminCliCommandProvider.java`.
- Plugins depend on `de.dlr.shepard:shepard-admin` at `<scope>provided</scope>` (verified in `plugins/video/pom.xml` line 190–207 and `plugins/unhide/pom.xml`).
- Backend has **no** `shepard-admin` dependency (`grep -n shepard-admin backend/pom.xml` returns zero matches in `<dependency>` blocks).
- Backend has **no** `quarkus.index-dependency.shepard-admin.*` entry.
- When Quarkus ArC at backend build time scans `VideoAdminCliCommandProvider` (or any pre-existing `MintersAdminCliCommandProvider` / `UnhideAdminCliCommandProvider`), the parent interface is referenced in the Jandex class metadata but the interface's `.class` file is not on backend's compile classpath nor in any indexed artifact.
- This is exactly the failure mode the task brief describes: `AutoAddScopeBuildItem.implementsInterface` walks the class hierarchy via `CompositeIndex.getClassByName(AdminCliCommandProvider)`, the lookup misses, recurses, and deadlocks on the `ConcurrentHashMap.computeIfAbsent` reservation node.

**Why it would suddenly trigger now and not before:** this is the
weakness in the hypothesis. The same pattern is used by `unhide`,
`minter-datacite`, and `minter-epic` — three pre-existing CLI command
providers. Adding `video` as a fourth implementor could plausibly push
Jandex into the bug (computeIfAbsent re-entry is sometimes load-dependent),
but I cannot prove the threshold from static inspection alone. **This is a
medium-confidence hypothesis, not high-confidence.**

## Second angle (also worth a probe)

**`PluginManifest` interface gained two new default methods this window**
(commit `ee07de0fd`, "PM1g — plugin self-declare public-path contributions"):

```java
default List<String> publicPaths() { return List.of(); }
default List<String> publicPathPrefixes() { return List.of(); }
```

Combined with the **new** `@ApplicationScoped` bean `PluginPublicPathRegistrar`
that `@Observes StartupEvent` and iterates every PluginManifest implementor,
this could force Jandex to re-walk every PluginManifest implementor's full
hierarchy. The `SpatiotemporalPluginManifest` is itself new (renamed from
`SpatialPluginManifest`).

This is consistent with the symptom (sudden hang in an existing pattern) but
also untestable from static inspection alone.

## Top recommendation

Single most likely one-line fix, **medium confidence**, to add to
`backend/src/main/resources/application.properties` adjacent to the existing
`quarkus.index-dependency.*` block:

```properties
quarkus.index-dependency.shepard-admin.group-id=de.dlr.shepard
quarkus.index-dependency.shepard-admin.artifact-id=shepard-admin
```

(Two lines — Quarkus's index-dependency config requires both group-id and
artifact-id keyed on the same identifier.)

This makes `AdminCliCommandProvider` resolvable in the CompositeIndex, which
should break the `getClassByName` recursion. The change is purely additive —
it doesn't bring `shepard-admin` onto the runtime classpath, only into the
build-time Jandex index. Risk is low: if `shepard-admin` isn't on the Maven
dependency graph at all, Quarkus will warn and skip; it won't break the build.

**If `shepard-admin` is rejected by Quarkus because it's not on the dependency
graph at all,** the alternative is to add it to `backend/pom.xml` at
`<scope>provided</scope>`:

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>shepard-admin</artifactId>
  <version>${revision}</version>
  <scope>provided</scope>
</dependency>
```

…and then the `quarkus.index-dependency.shepard-admin` entry above.

## Fallback hypotheses (if top recommendation doesn't pan out)

1. **picocli (`info.picocli:picocli` 4.7.6).** `VideoCommand`, `VideoStatusCommand`,
   `VideoSetFfprobeEnabledCommand`, `VideoSetMaxFileSizeCommand` carry
   `@picocli.CommandLine.Command` class-level annotations. picocli is `provided`
   in plugins/video/pom.xml line 202–207 and **not** in
   `quarkus.index-dependency.*`. The same pattern in unhide previously worked,
   but adding video may push Jandex over an internal threshold. One-line fix
   would be:
   ```properties
   quarkus.index-dependency.picocli.group-id=info.picocli
   quarkus.index-dependency.picocli.artifact-id=picocli
   ```
2. **Bisect path (S3 in the existing investigation).** If the
   index-dependency additions don't unstick the build, bisect commits
   `6b3550d30..HEAD` — the 277-commit window is large but most are docs.
   Filter to feature/refactor commits: `git log --oneline 6b3550d30..HEAD
   --grep="^feat\|^refactor"` gives ~70 candidates. Bisect should find the
   trigger commit in ~7 steps.

## What I couldn't determine

- **Whether `AdminCliCommandProvider` was already unresolvable at
  6b3550d30.** If yes, then adding video as a fourth implementor is the
  trigger (the load threshold argument); if no, then something else changed.
  I could not verify this without a working build at 6b3550d30 to compare.
- **Whether the picocli annotation alone (without backend importing picocli)
  triggers AutoAddScope.** Quarkus's behaviour with provided-scope annotations
  on bean classes is version-dependent (Quarkus 3.27.2 vs earlier may differ).
- **Whether the new default methods on `PluginManifest`
  (`publicPaths`, `publicPathPrefixes`) interact with Jandex's
  `AnnotationOverlayImpl`** in a way that triggers the recursion. The
  AnnotationOverlay is specifically what's deadlocked in the stack trace,
  and interface evolution + a new `@ApplicationScoped` consumer
  (`PluginPublicPathRegistrar`) is a real-shape change in this window.
- **Whether the build actually hangs on the renamed
  `shepard-plugin-spatiotemporal` artifact specifically**, vs. one of
  the other classes. Without a successful debug build (`-X` +
  `quarkus.log.category."io.quarkus.arc".level=TRACE`), I cannot see which
  class name was last logged before the wedge.

## What's NOT the culprit

- **`fileformat-thermography` is standalone** — verified in
  `plugins/fileformat-thermography/pom.xml` header comment: explicitly not
  wired into aggregator, not a backend dependency. `FileParserPlugin`
  interface is internal to that module.
- **`PolicyDecisionPoint`, `PayloadExportHandler`** are new internal
  backend interfaces — backend's own classes are always indexed.
- **The MFFD-NDT-GRID widget is frontend-only** (no backend Java changes).
- **OTvis tier-1/2 plugin module is standalone** (same as thermography
  — they are the same module, `plugins/fileformat-thermography/`).
- **All `*Config` entities implementing `HasAppId`** (VideoConfig, AasConfig,
  InstanceRegistry) follow the same shape as pre-existing `UnhideConfig`,
  `SemanticConfig`, `AIConfig` — proven pattern, can't be the trigger.

## Recommended next step

If the operator can spare 5 minutes, try the top-recommendation two-line addition
to `application.properties` and rebuild with a 15-minute timeout. If the build
completes, this hypothesis is confirmed; file a follow-up to permanently document
the requirement that any new plugin-implementing-cli-SPI also gets an
index-dependency entry.

If the build still hangs, fall back to the bisect path — feature/refactor-only
filter on commits `6b3550d30..HEAD` brings the search space to ~70 commits,
~7 bisect steps, each capped at a 15-minute build timeout.
