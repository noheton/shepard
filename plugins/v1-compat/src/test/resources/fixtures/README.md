# v1-compat plugin — fixture corpus pointer

This directory is intentionally empty in Phase 1 (per the design
clarification 4 lean: **the v5 wire-fidelity fixture corpus stays in
core**). The plugin holds the contract; the fixtures live where the
classes under test live.

When the v5 fixture corpus lands (task #128 / `V5WireFidelityTest`),
it lives at:

```
backend/src/test/resources/fixtures/v5/
```

…alongside `backend/src/test/java/.../V5WireFidelityTest.java` and
`backend/src/test/java/.../V5JsonNormalizer.java`. The plugin's
PluginManifest documents what byte-stability the corpus enforces;
the corpus itself stays under core ownership because:

1. Today (Phase 1) the v1 REST classes still live in core — moving
   the fixtures alone would break the standard "test next to code"
   convention without buying anything.
2. Phase 2 will move the v1 REST classes themselves into this
   plugin JAR. At that point the corpus follow-on (or not) is one
   of `aidocs/platform/103 §5.1`'s hard clarifications. The Phase 1
   design lean is "keep the corpus in core forever" — phase-routing
   between surefire and failsafe is the real decision, not the
   directory move.

This README exists so a contributor browsing the plugin module
isn't surprised by the empty fixtures directory.

References:
- `aidocs/platform/103a-v1-compat-marker-plugin.md` §2 row 7 + §4 clarification 4
- `aidocs/platform/103-v1-compat-plugin-extraction.md` §5.1 (Phase 2)
- `docs/reference/v5-cross-instance-quirks.md` (when v5 fixture corpus lands)
