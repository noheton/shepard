# feat-krl-transform

**Proves:** B5 — a KUKA KRL `.src` + URDF materialized into a derived joint-trajectory
`TimeseriesReference`. The `krl-interpreter` plugin dissolves the bespoke KRL interpret
subsystem into the generic MAPPING_RECIPE mechanism: a `MAPPING_RECIPE` template binds a
KRL `.src` FileReference (FR1b singleton) + a URDF FileReference, and materializing it via
`POST /v2/mappings/{appId}/materialize` dispatches to the `KrlTrajectoryTransformExecutor`
(`TransformExecutor` SPI), which calls the KRL interpreter sidecar (forward kinematics over
the URDF), persists the joint trajectory as a NEW `TimeseriesReference`, and returns its
appId.

**Run:**
```bash
/tmp/reseed-venv/bin/python seed.py --apikey "$(cat /tmp/reseed_apikey.txt)" \
  --host http://localhost:8080 --reset
```

Idempotent; `--reset` deletes the collection first. The seed degrades gracefully and
notes a gap if the executor is unregistered (plugin disabled) or the KRL sidecar is
unreachable.

**Green:** verified end-to-end — the materialize calls the KRL interpreter sidecar
(`shepard-krl-interpreter`, alias `krl-interpreter-sidecar:8000`) and persists a derived
joint-trajectory `TimeseriesReference`, which the seed GET-verifies.

Two enablement bugs were found and **fixed this PR** to get here (aidocs/integrations/121 §5):
- `RESEED-FIND-KRL-BEANS` — the executor reaches its `@ApplicationScoped
  KrlTrajectoryService` via a dynamic `CDI.current().select(...)` lookup that Quarkus
  Arc's build-time dead-code elimination can't see, so it pruned the bean ("No bean
  found"). Fixed by adding the service to `quarkus.arc.unremovable-types`.
- The plugin must be enabled at **deploy time** (`SHEPARD_PLUGINS_KRL_INTERPRETER_ENABLED=true`)
  — a runtime `PATCH /v2/admin/plugins` toggle doesn't persist (`PluginRuntimeOverride`
  is an unregistered OGM entity, a separate core finding).
