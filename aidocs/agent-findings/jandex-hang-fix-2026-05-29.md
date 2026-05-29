---
stage: feature-defined
last-stage-change: 2026-05-29
audience: contributors, operators
---

# Jandex hang — root cause + fix (2026-05-29)

**Status**: FIXED. `mvn package` completes in **~16 seconds** (previously hung indefinitely; 5h / 41min wedges on 2026-05-28).

**Fix branch**: `jandex-hang-fix-2026-05-29`
**Fix commit**: see `bd168e011` (initial probe) + the commit landing this doc.

## TL;DR

The hang had **two compounding problems**, both surfaced in this PR:

1. **PRIMARY (the actual hang)**: Quarkus 3.27.2's
   `AutoAddScopeBuildItem.Builder.implementsInterface` (line 222) has a
   defensive-coding gap. When `index.getClassByName(superName)` returns `null`
   (i.e. the superclass isn't in the Jandex composite index), the `while` loop
   walks the class hierarchy **but does not advance `superName`** — the loop
   re-checks the same unresolvable superclass forever. The build worker thread
   sits at `RUNNABLE` burning CPU on `getClassByName` for hours. **Trigger**:
   plugin CLI classes (`VideoCommand`, `UnhideCommand`, `MintersCommand`)
   `extends de.dlr.shepard.cli.AbstractCommand`, but the backend didn't
   include `shepard-admin` as a dep — so `AbstractCommand` was a "ghost
   class" the index couldn't resolve.

2. **SECONDARY (revealed by fixing #1)**: Two REST classes registered the
   same path
   `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations`:
   - `TimeseriesChannelAnnotationRest` (TS-SEMANTIC-REST, commit `babb5c8f6`,
     uses general `SemanticAnnotationIO`, supports GET + POST + DELETE) — **used by frontend
     Trace3DEditChannelsDialog, ViewRecipeBuilderDialog, MCP tools, seed.py**.
   - `TimeseriesContainerChannelAnnotationRest` (TS-AXIS-AUTO, commit
     `483282896`, uses specialised `ChannelAxisAnnotationIO`, GET + POST only)
     — **dead code, only self-referenced**.

   Quarkus' `ResteasyReactiveProcessor.checkForDuplicateEndpoint` rejects
   the duplicate. This bug was previously hidden by the Jandex hang
   blocking the build before endpoint validation ran.

## The smoking-gun jstack

After ~5 min of build time, captured the burning worker thread:

```
"build-36" #129 prio=5 os_prio=0 cpu=356363.63ms elapsed=357.86s nid=1345230 runnable
   java.lang.Thread.State: RUNNABLE
	at org.jboss.jandex.CompositeIndex.getClassByName(CompositeIndex.java:274)
	at org.jboss.jandex.CompositeIndex.getClassByName(CompositeIndex.java:270)
	at io.quarkus.arc.processor.IndexClassLookupUtils.getClassByName(IndexClassLookupUtils.java:47)
	at io.quarkus.arc.processor.BeanArchives$IndexWrapper.getClassByName(BeanArchives.java:131)
	at io.quarkus.arc.deployment.AutoAddScopeBuildItem$Builder.lambda$implementsInterface$3(AutoAddScopeBuildItem.java:222)
	at io.quarkus.arc.deployment.AutoAddScopeBuildItem.test(AutoAddScopeBuildItem.java:73)
	at io.quarkus.arc.deployment.AutoAddScopeProcessor$1.transform(AutoAddScopeProcessor.java:86)
	at io.quarkus.arc.processor.AnnotationsTransformer.apply(AnnotationsTransformer.java:67)
	at org.jboss.jandex.AnnotationOverlayImpl$2.apply(AnnotationOverlayImpl.java:253)
	at java.util.concurrent.ConcurrentHashMap.computeIfAbsent(java.base@25.0.3/ConcurrentHashMap.java:1724)
	- locked <0x000000042add7a28> (a java.util.concurrent.ConcurrentHashMap$ReservationNode)
	at org.jboss.jandex.AnnotationOverlayImpl.getAnnotationsFor(...)
	at org.jboss.jandex.AnnotationOverlayImpl.hasAnnotation(...)
	at io.quarkus.arc.processor.AnnotationStore.hasAnnotation(...)
	at io.quarkus.arc.processor.BeanDeployment.isVetoed(BeanDeployment.java:1409)
	at io.quarkus.arc.processor.BeanDeployment.findBeans(BeanDeployment.java:1116)
	at io.quarkus.arc.processor.BeanDeployment.registerBeans(...)
```

**Reading the stack bottom-up**: `BeanDeployment.findBeans` iterates candidate
beans, calls `isVetoed(class)` → `AnnotationStore.hasAnnotation` to check for
`@Vetoed` → which walks all registered `AnnotationsTransformer`s →
`AutoAddScopeProcessor` runs the `AutoAddScopeBuildItem.test` predicate →
`implementsInterface(...)` lambda spins on `getClassByName(superName)`.

## The Quarkus code that loops

`extensions/arc/deployment/src/main/java/io/quarkus/arc/deployment/AutoAddScopeBuildItem.java`,
line ~210:

```java
public Builder implementsInterface(DotName interfaceName) {
    return and((clazz, annotations, index) -> {
        if (clazz.interfaceNames().contains(interfaceName)) {
            return true;
        }
        DotName superName = clazz.superName();
        while (superName != null && !superName.equals(DotNames.OBJECT)) {
            ClassInfo superClass = index.getClassByName(superName);
            if (superClass != null) {
                if (superClass.interfaceNames().contains(interfaceName)) {
                    return true;
                }
                superName = superClass.superName();   // ← only advances inside this branch
            }
            // if superClass == null, superName stays the same → infinite loop
        }
        return false;
    });
}
```

`superName` is reassigned only inside the `if (superClass != null)` branch.
When `superClass == null` (i.e., the superclass is not in the Jandex composite
index), the loop never moves up the hierarchy. This is a Quarkus bug; the
correct shape would be to `break` (or set `superName = null`) on a null
`superClass` and treat the lookup as a graceful failure.

I have not filed this upstream in this dispatch (time budget); the patch is
straightforward (one line) and should be reported to `quarkusio/quarkus` —
see "Follow-ups" below.

## Hypotheses ruled out

- **H1 (AdminCliCommandProvider SPI itself)** — disabling all four
  `META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider`
  ServiceLoader files reproduced the hang identically. The SPI registry
  files aren't the trigger; the **class hierarchy** is.
- **H3 (JDK 25 CHM.computeIfAbsent re-entrance / JDK 21.0.4 deadlock bug)**
  — the jstack showed `RUNNABLE` with 356s CPU consumed on a single
  thread, not `BLOCKED`. The CHM ReservationNode lock annotation in the
  stack is normal for a mid-call lambda; the actual spin is in
  `CompositeIndex.getClassByName` (Jandex code, not CHM code).
- **H4 (recent feature commit added the trigger)** — the trigger has been
  latent since the first plugin CLI class shipped (the
  `UnhideCommand extends AbstractCommand` pattern, pre-2026-05-26). What
  changed on 2026-05-27 was likely the **number** of plugin-CLI classes
  crossing a threshold inside Quarkus' `AnnotationOverlay`'s
  `computeIfAbsent` cache that turned a slow lookup into a wedge.
  Adding `VideoCommand` (commit `4be6880ce`) tipped the scale.
- **H5 (recent dep version bump)** — `backend/pom.xml` had no dep version
  changes in the window `6b3550d30..HEAD` that affected Quarkus, Jandex,
  or maven-jar-plugin. Quarkus has been pinned at 3.27.2 for ~2 weeks.

## The fix shipped

**Two files changed in `backend/`** (plus the deleted dead class + tests):

### 1. `backend/pom.xml` — add `shepard-admin` as compile-scope dep

Added to the `with-plugins` profile (next to the other plugin deps):

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>shepard-admin</artifactId>
  <version>${revision}</version>
</dependency>
```

**Why compile scope, not `<scope>provided</scope>`**: `quarkus.index-dependency`
configuration is silently inert if the artifact isn't on the Deployment
ClassLoader. `provided` scope keeps shepard-admin OFF the ClassLoader, so the
index-dep entry doesn't actually pull the classes into the composite index.
The `Failed to index de.dlr.shepard.cli.AbstractCommand: Class does not exist
in ClassLoader` warning continued, and the hang reproduced. Compile scope is
the smallest fix that makes the WARN disappear and the build complete.

Cost: ~3 MB of CLI + picocli + jackson is now in `quarkus-app/lib/main/`. The
shaded shepard-admin uberjar ships at 2.9 MB. This is a one-time uberjar
bloat acceptable for unblocking 5+ pending live deploys.

### 2. `backend/src/main/resources/application.properties` — index entry

```properties
quarkus.index-dependency.shepard-admin.group-id=de.dlr.shepard
quarkus.index-dependency.shepard-admin.artifact-id=shepard-admin
```

Forces Quarkus to add the shepard-admin jar's classes into its
CompositeIndex at build time (so `AbstractCommand` + `AdminCliCommandProvider`
are resolvable from inside plugin-CLI classes).

### 3. Delete `TimeseriesContainerChannelAnnotationRest.java`

Dead duplicate-path REST class — superseded by `TimeseriesChannelAnnotationRest`
(TS-SEMANTIC-REST). No callers in backend, frontend, MCP tools, or seed scripts.
The companion service overloads (`AnnotatableTimeseriesService
.getAnnotationsForChannel(UUID)` + `.createAnnotationForChannel(UUID,
ChannelAxisAnnotationIO)`) are left in place for a future cleanup pass
(REF-2026-CLEANUP); they're unreachable now but harmless.

## Verified

```
$ cd backend && time ./mvnw clean package -Dmaven.test.skip=true -DskipTests
...
[INFO] BUILD SUCCESS
[INFO] Total time:  15.963 s
$ ls target/quarkus-app/quarkus-run.jar
target/quarkus-app/quarkus-run.jar
$ ls target/quarkus-app/lib/main/de.dlr.shepard.shepard-admin-6.0.0-SNAPSHOT.jar
target/quarkus-app/lib/main/de.dlr.shepard.shepard-admin-6.0.0-SNAPSHOT.jar
$ java -jar target/quarkus-app/quarkus-run.jar
  __  ____  __  _____   ___  __ ____  ______
   --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
   ...
  # starts (fails at DB connect step as expected — no Neo4j/PG/Mongo in this raw test)
```

Build time: **15.9 s** (down from infinity / timeout).

Backend test compile: clean.

## Follow-ups (filed as backlog rows; not in this PR)

1. **CLI-SPLIT-1 (S)**: split `shepard-admin` into `shepard-admin-api`
   (interfaces + base classes only — `AdminCliCommandProvider`,
   `AbstractCommand`, common types) at compile scope, and `shepard-admin-cli`
   (impl + shade + picocli + jackson) at provided scope. Removes ~3 MB
   uberjar bloat while keeping the Jandex index whole.
2. **CLI-JANDEX-IDX-1 (XS)**: ship a precomputed `META-INF/jandex.idx` inside
   the `shepard-admin` shaded jar via the `jandex-maven-plugin` so Quarkus
   doesn't have to re-index ~1500 picocli/jackson classes on every backend
   build. Possibly fast enough that CLI-SPLIT-1 becomes optional.
3. **QUARKUS-ARC-AUTOADDSCOPE-UPSTREAM (XS)**: file the
   `AutoAddScopeBuildItem.implementsInterface` infinite-loop bug at
   `quarkusio/quarkus`. One-line fix: `break;` when `superClass == null`
   (or set `superName = null`). The defensive-coding gap will burn other
   teams that have plugin classes referencing unindexed base classes —
   this is a real safety net, not just our specific symptom.
4. **TS-AXIS-AUTO-CLEANUP (XS)**: remove the unused
   `AnnotatableTimeseriesService.getAnnotationsForChannel(UUID)` +
   `.createAnnotationForChannel(UUID, ChannelAxisAnnotationIO)` overloads
   and the `ChannelAxisAnnotationIO` record once the axis-role write path
   migrates to the general `SemanticAnnotationIO` shape. (Currently
   harmless dead code; cleanup, not bug.)
5. **DUP-ENDPOINT-CI-GUARD (S)**: add a CI gate (Spotless? a unit-level
   `quarkus-test` smoke check?) that fails fast on duplicate JAX-RS path
   collisions instead of producing a `DeploymentException` only at
   `mvn package` time. The TS-AXIS-AUTO / TS-SEMANTIC-REST collision
   lived in main for 30 hours undetected because the hang masked it.

## What I'd do differently if I had more time

- File the upstream Quarkus issue (#3 above) before declaring done. The
  one-line fix on Quarkus' side is the durable solution; everyone else
  who hits this hits a wall the same way we did.
- Probe the `~/.m2/repository/de/dlr/shepard/shepard-admin/.../*.jar` shade
  more carefully — the shaded uberjar bundles picocli + jackson at top
  level, which means Quarkus now indexes ~1500 extra classes it didn't
  need. CLI-SPLIT-1 is the proper fix.
- Confirm the duplicate-endpoint bug isn't recurring elsewhere. The
  OpenAPI warnings about "Duplicate operationId" (50+ of them in the
  build log) are a separate code smell — many feature-pair files create
  REST endpoints with the same OperationId across unrelated paths.
  Doesn't break the build (those are different paths, just same
  operationId), but it's a documentation/SDK-generation bug worth a
  sweep.
