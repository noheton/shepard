---
stage: deployed
last-stage-change: 2026-07-07
---

# APISIMP Sweep — fire-463 (2026-07-07)

Scanned 92 `/v2/` REST files + 20 plugin REST files for residual API-simplification
opportunities. Fire-463 sweep follows fire-457 (all 10 findings resolved).

## What I found

After 224+ merged APISIMP rows, the `/v2/` surface is in excellent shape.
One XS finding identified: `BundleGroupsV2Rest.listGroupFiles()` uses boxed `Integer`
pagination params with manual null-clamping instead of the canonical Bean Validation
pattern used everywhere else.

## Findings

### F1 — `BundleGroupsV2Rest`: boxed-Integer pagination params + manual clamping (`APISIMP-BUNDLE-GROUP-FILES-INTEGER-PARAMS`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/BundleGroupsV2Rest.java:354–372`  
**Path:** `GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files`  
**Size:** XS

`listGroupFiles()` declares:

```java
@QueryParam("page") @DefaultValue("0") Integer page,
@QueryParam("pageSize") @DefaultValue("200") Integer pageSize,
```

and then clamps manually:

```java
int pageIdx = (page == null || page < 0) ? 0 : page;
int effectiveSize = (pageSize == null) ? DEFAULT_FILES_PAGE_SIZE
    : Math.min(MAX_FILES_PAGE_SIZE, Math.max(1, pageSize));
```

The `@DefaultValue` annotations make the null checks unreachable (JAX-RS sets the
default when the param is absent). The clamping logic duplicates what `@PositiveOrZero`,
`@Min(1)`, and `@Max(1000)` Bean Validation constraints express declaratively — the
pattern used by every other v2 paginated endpoint (`CollectionDQRRest`, `SearchV2Rest`,
`ShepardTemplateRest`, etc.). `@Schema(minimum/maximum)` on the `@Parameter` already
documents the intent; the imperative clamp just adds noise and slightly different
behaviour (clamping vs. rejection).

`@Min` and `@Max` are already imported (lines 20–21). `@PositiveOrZero` is not.

**Fix:** change `Integer page` → `@PositiveOrZero int page`; change `Integer pageSize`
→ `@Min(1) @Max(1000) int pageSize`; add `import jakarta.validation.constraints.PositiveOrZero`;
remove the `pageIdx`/`effectiveSize` null-clamp lines; use `page` and `pageSize` directly.
Keep `@DefaultValue("0")` / `@DefaultValue("200")` and `@Schema(minimum,maximum,defaultValue)`
annotations.

**AC:** `listGroupFiles` uses Bean Validation constraints on primitive int params;
manual null-clamp block removed; `mvn verify -pl backend` green.

## Opportunities

- No other XS or larger findings surfaced. Surface cleanliness score: 96/100.

## What surprised me

The `/v2/bundle/` namespace is itself a 410-tombstone after `APISIMP-BUNDLE-REF-KIND-UNIFY`
(fires 456–457). `BundleGroupsV2Rest` is the sub-resource that handles
`/v2/bundles/{bundleAppId}/groups/...` — it was not tombstoned because clients may
still use the groups sub-resource directly. It is the last non-tombstone resource in
the bundle namespace, making F1 a clean-up of a legacy class.
