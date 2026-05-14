# 69 — Runtime plugin CDI integration (deferred PM1b3)

**Status.** Design draft.
**Snapshot date.** 2026-05-14.
**Audience.** Maintainer + plugin authors evaluating the gap
between PM1's "drop a JAR into `/deployments/plugins/`" promise and
Quarkus's build-time CDI scanning model.

**Originating items.** PM1b2 dispatch (`aidocs/16` PM1b2) +
ADR-0023 forward-work checklist + CLAUDE.md plugin-first reminder.
Couples to `aidocs/47 §2.5` (plugin SPI seam), `aidocs/68 §3` (the
plugin-vs-core overview), `aidocs/63 ADR-0023` / ADR-0024 (the
distribution decision that PM1b3 will reverse for CDI plugins).

---

## 1. The gap

PM1a–PM1e shipped the plugin SPI scaffolding: `PluginManifest`,
`PluginRegistry`, admin REST + CLI, persistent runtime overrides,
manifest enrichment + dependency resolution, CLI extensibility.
Three plugins ship today: UH1a (Helmholtz Unhide publish), KIP1g
(HMC Kernel Information Profile resolver), KIP1h (LocalMinter).

**The user-facing promise** (CLAUDE.md plugin-first, the
`docs/reference/plugins.md` operator runbook): *"drop a
`shepard-plugin-foo-x.y.z.jar` into `/deployments/plugins/`,
restart, and the plugin's REST endpoints + admin CLI subcommand
come up."*

**What actually happens today**: the manifest is discovered
through `ServiceLoader` and the lifecycle hooks fire, but if the
plugin ships any `@ApplicationScoped` / `@RequestScoped` /
`@Path`-annotated beans, **Quarkus's build-time CDI scanner** never
indexed the JAR's classes — so the REST resources don't show up and
the beans don't resolve. Operators sidestep this today by
declaring the plugin as a Maven `<dependency>` in the backend's
default-active `with-plugins` profile + the
`quarkus.index-dependency.shepard-plugin-foo.*` keys in
`application.properties`. That works for plugins shepard ships
in-tree (UH1a, KIP1g, KIP1h) but **doesn't generalise to a vendor's
drop-in JAR** — the operator can't add to the backend pom at
runtime.

PM1b2 introduced the `DEGRADED` state to surface this gap to
operators (the row appears in `GET /v2/admin/plugins` so the
operator knows the plugin's lifecycle hooks ran but its beans
aren't active). PM1b3 closes the gap: a runtime-only JAR's CDI
beans become active without a backend rebuild.

---

## 2. Why this is hard with Quarkus

Quarkus's CDI container (Arc) does its scanning at **build time**
(`quarkus:build` / `quarkus:dev` startup). The list of beans is
baked into the application's image at compile time. Three
constraints flow from this:

1. **No runtime bean addition.** Arc doesn't expose an API to
   "register this `@ApplicationScoped` bean class at runtime."
   The bean's metadata (qualifiers, scopes, interceptor bindings,
   producer methods) is gone from the JAR's class metadata by the
   time the app starts — Arc compiled them into an indexed table.
2. **No runtime JAX-RS endpoint addition.** RESTEasy Reactive
   (the JAX-RS impl Quarkus uses) builds its routing table at
   `quarkus:build` time. There's no "register this `@Path`
   resource class at runtime" hook.
3. **Build-time index dependency.** Even the
   `quarkus.index-dependency.*` config keys we use today require
   the JAR to be on the **compile** classpath at `quarkus:build`
   time — they're hints to Jandex, not to Arc.

The result: today's PM1 plugin shape compiles plugins **into** the
backend image. The `/deployments/plugins/` drop-in directory is a
visibility convenience, not a true runtime extension surface.

---

## 3. The three options

| Option | Shape | Cost | Reach |
|---|---|---|---|
| **A** | Quarkus Extension per plugin | high — every plugin author writes a Quarkus extension (`@BuildStep` etc.) | full Quarkus integration; the upstream-recommended path |
| **B** | Vert.x router for plugin REST | medium — plugins ship a non-CDI Vert.x handler instead of `@Path` resources | works for REST; doesn't fix `@Inject` |
| **C** | Native-image-friendly second JVM | very high — plugins run in a sidecar JVM with shepard core as a client | breaks the single-process operator model |

**Recommendation: Option B**, with a small extension of the
`PluginContext` SPI so plugins can register Vert.x handlers
declaratively. Rationale:

- **Pragmatic for the 80% case.** Most plugins shepard envisages
  expose one or two REST endpoints (UH1a's feed, KIP1g's
  resolver, a future video plugin's upload endpoint). A small
  helper API on `PluginContext` keeps the plugin authoring
  experience close to the existing JAX-RS shape without each
  vendor writing a Quarkus extension.
- **Side-steps CDI.** A Vert.x handler doesn't need
  `@ApplicationScoped`. It accesses core services through the
  `PluginContext.beanManager()` lookup that already works
  (manifest lifecycle hooks demonstrated this in PM1a).
- **Backward-compatible.** Existing in-tree plugins keep working
  as today (declared in `with-plugins`, picked up by build-time
  CDI). The new SPI is opt-in for vendors who want true drop-in.
- **No native-image penalty.** Quarkus's native-image build
  doesn't care about Vert.x routes added at runtime — they're
  data, not bean metadata.

Option A (per-plugin Quarkus extension) stays available for
vendors who want full Quarkus integration. The two options
coexist; PM1b3 ships Option B + a follow-up doc explains how a
vendor would write a Quarkus extension if they need the deeper
hook.

---

## 4. PM1b3 implementation sketch

### 4.1 `PluginContext` extension

```java
public interface PluginContext {
  // … existing methods …

  /**
   * PM1b3 — register a Vert.x route the plugin exposes. The route
   * is added to the running app's router so requests under the
   * given path prefix are dispatched to the handler.
   *
   * <p>The handler must be thread-safe (Vert.x handlers run on
   * the event loop). Plugins access core services through
   * {@link #beanManager()}.
   */
  void registerHttpRoute(String pathPattern, Handler<RoutingContext> handler);

  /**
   * PM1b3 — register a CLI subcommand. Already implemented by PM1d
   * via the AdminCliCommandProvider SPI; called out here for
   * completeness so plugin authors see the full surface.
   */
  // (no signature change; existing AdminCliCommandProvider stays)
}
```

### 4.2 Registry wiring

`PluginRegistry` keeps its current shape; the new
`registerHttpRoute` calls flow into a singleton `PluginRouter`
that wraps Quarkus's `io.vertx.ext.web.Router`. On
`onRegister(ctx)`, plugins call `ctx.registerHttpRoute(...)` and
the route becomes live immediately.

### 4.3 Migration path for UH1a / KIP1g / KIP1h

The three existing in-tree plugins stay as they are. PM1b3 ships
without touching them. A new "vendor-style" example plugin (e.g.
`plugins/example-video/`) demonstrates the Option B shape — its
manifest declares `dependencies = []`, ships zero
`@ApplicationScoped` beans, registers its `/v2/video/upload`
handler through `registerHttpRoute`, and is **not** declared in
the backend's `with-plugins` profile (proving the drop-in shape
works end-to-end).

### 4.4 Detection logic (PM1b3 closes PM1b2's DEGRADED detection)

Today the `DEGRADED` state exists but the registry doesn't detect
which plugins need it. PM1b3 wires the detection:

- On JAR-walk discovery, check whether the JAR's
  `META-INF/quarkus-extension.yaml` (or our convention's
  `META-INF/services/PluginManifest`) declares CDI beans without a
  matching `quarkus.index-dependency.*` config key. If so, mark
  the entry DEGRADED with `plugin.runtime.no-cdi-scan` and
  surface the operator runbook pointer in `failureMessage`.
- A plugin using the new `registerHttpRoute` API doesn't need CDI
  scanning and stays ENABLED.

---

## 5. Why this is deferred (PM1b2 scope decision)

The PM1b2 dispatch listed Option B (the design doc) as an
acceptable fall-back if Option A (a working POC) got gnarly
within reasonable scope. After exploring the Quarkus internals:

- Arc's `@BuildStep`-driven bean registration genuinely doesn't
  expose a runtime hook (verified against Quarkus 3.27.x source).
- A working POC would need to ship a small Quarkus extension
  (`@BuildStep AdditionalBeanBuildItem` on a fixed set of plugin
  classes scanned at build time) — but that "fixed set" is
  exactly what the `with-plugins` profile already provides, so
  the POC would just rename the existing shape, not unlock true
  drop-in.
- The Vert.x router path (Option B above) is the structurally
  correct fix and deserves its own slice rather than being
  cargo-culted into PM1b2.

PM1b2 instead ships:

1. The `DEGRADED` state + lifecycle hook
   ({@link PluginEntry#markDegraded}) — so the operator visibility
   for "discovered but inert" is in place ahead of PM1b3's
   detection logic.
2. The JAR signature verifier + the semver-range enforcement of
   `shepardCompatibility()` — the two other hardening items that
   are strictly orthogonal to the CDI integration.

A row in `aidocs/16` tracks PM1b3 as queued; this doc is the
forward-work parking spot.

---

## 6. Open questions for PM1b3

1. **Hot reload vs. restart-required.** Vert.x route registration
   works at runtime — but does an operator who drops a new JAR
   need to restart? Probably yes for PM1b3's first cut; "hot
   reload of a runtime JAR" is its own slice.
2. **Security perimeter.** A plugin registering an HTTP route at
   `/v2/foo` could shadow a core route. Decision: namespace
   plugin routes under `/v2/plugins/<plugin-id>/...` by default;
   allow explicit override only when the plugin's manifest
   declares the route in a top-level field.
3. **Failure-mode taxonomy.** New states: route conflict
   (`plugin.route.conflict`), handler init failure
   (`plugin.route.init-failed`). Both flow through the same
   `failureMessage` field PM1b2 expanded.
4. **CLI ergonomics.** Should the operator's
   `shepard-admin plugins list` table grow a `ROUTES` column
   showing each plugin's registered paths? Defer to PM1b3 dispatch.

---

## 7. Decision

**Recommendation:** defer the runtime plugin CDI integration to
PM1b3 as a standalone slice. PM1b2 ships the prerequisites
(`DEGRADED` state + signature verifier + compat enforcement);
PM1b3 picks up the `registerHttpRoute` SPI extension and the
`PluginRouter` implementation.

**Status:** queued. Tracker rows updated:

- `aidocs/16` — new PM1b3 row queued.
- `aidocs/44` — feature-matrix row for "runtime plugin CDI"
  status: 📐 designed (PM1b2) → 🚧 in-flight (PM1b3) → ✓ shipped
  (PM1b3 lands).
- `aidocs/63 ADR-0023` forward-work checklist — child-classloader
  CDI integration ticked as "deferred to PM1b3 (see
  aidocs/69)".
