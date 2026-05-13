# `backend/plugins/` — shepard plugin drop-in directory

This directory is the **discovery mount-point** for shepard's
plugin SPI (PM1a, ADR-0023). On startup — after database
migrations apply but before the REST endpoints come up — the
backend walks this directory for `*.jar` files and discovers
each plugin's `PluginManifest` via Java's `ServiceLoader`.

## For operators

Install a plugin:

```bash
cp shepard-plugin-foo-X.Y.Z.jar /path/to/shepard/backend/plugins/
# then restart shepard
```

Uninstall:

```bash
rm /path/to/shepard/backend/plugins/shepard-plugin-foo-*.jar
# then restart shepard
```

Inspect what's installed:

```bash
shepard-admin plugins list
# or via the REST API:
curl -H "Authorization: Bearer $TOKEN" https://shepard.example.com/v2/admin/plugins
```

Toggle a plugin without removing the JAR (runtime override —
does not survive a restart; use `application.properties` for a
persistent install default):

```bash
shepard-admin plugins disable unhide
shepard-admin plugins enable unhide
```

Full operator runbook: `docs/reference/plugins.md`.

## For developers

The plugin SPI is in `de.dlr.shepard.plugin.*`. A plugin module
implements `PluginManifest` and ships a
`META-INF/services/de.dlr.shepard.plugin.PluginManifest` file
naming its implementation class. See `plugins/unhide/` (UH1a) as
the canonical example.

## Notes

- This directory is **not** committed to git beyond this README
  and a `.gitkeep`. JARs land here at image-build time (the
  Dockerfile copies `plugins/*/target/shepard-plugin-*.jar` here)
  or at install time (operators dropping in vendor-supplied JARs).
- The directory's location is configurable via
  `shepard.plugins.dir` in `application.properties` — change it
  if your container layout puts plugins elsewhere.
- Phase 1 (PM1a) plugins that lean on Quarkus build-time CDI
  scanning also ship as Maven `<dependency>` lines in the backend
  pom. The drop-in JAR is the **operator-facing artefact**;
  full-runtime drop-in for non-CDI plugins works out of the box.
  See ADR-0024 (and the PM1b backlog row in `aidocs/16`) for the
  pure-runtime path.
