# Install — shepard-plugin-fileformat-thermography

**Status:** alpha · standalone module · NOT yet wired into the main
aggregator build.

## Current shape (tier-1, OTVIS-PARSE-1)

The module is a **standalone Maven project**. Its `pom.xml` is
deliberately NOT referenced from `/opt/shepard/plugins/pom.xml` or
`/opt/shepard/backend/pom.xml`. Adding it to those aggregators would
make the backend's known-broken build worse (the Quarkus / Jandex
`CompositeIndex.getClassByName` infinite-loop hang).

For now, the plugin is built independently and the resulting jar is a
deployable artefact that **cannot yet be loaded by a running backend**
— wiring the artefact into the runtime classloader is the follow-up
row `OTVIS-WIRE-AGGREGATOR-1`, gated on the Jandex hang being fixed.

## Build

```sh
cd plugins/fileformat-thermography
mvn package
```

Output: `target/shepard-plugin-fileformat-thermography-0.1.0-SNAPSHOT.jar`.

## Test

```sh
cd plugins/fileformat-thermography
mvn test
```

Expected: **27 tests, 0 failures**. Tests load a real fixture
(`src/test/resources/sample_S4_M13_L18_F4.OTvis`, 8.4 MB, MFFD upper
shell, DLR ZLP Augsburg, 2024-02-07) and assert the full tier-1
annotation set lands on a recording `AnnotationWriter`.

## Dependencies

| Dependency | Version | Licence | Why |
|---|---|---|---|
| `org.apache.commons:commons-compress` | 1.27.1 | Apache-2.0 | POSIX tar reader (Edevis `.OTvis` is a tar) |
| `org.junit.jupiter:junit-jupiter` (test) | 5.11.4 | EPL-2.0 | Tests |
| `org.assertj:assertj-core` (test) | 3.27.3 | Apache-2.0 | Tests |

No external services, no sidecar containers, no compose changes.

## Runtime configuration

No runtime configuration knobs in tier-1. Tier-2 will add a
`:ThermographyConfig` admin singleton for the frame-extraction
buffer-size + output-format settings.

## Wire-up (when backend Jandex hang clears)

Follow-up row: `OTVIS-WIRE-AGGREGATOR-1` (queued).

Steps the wire-up commit will take:

1. Replace the local `de.dlr.shepard.plugin.fileformat.thermography.FileParserPlugin`
   shim with `import de.dlr.shepard.spi.fileparser.FileParserPlugin`.
2. Add `@ApplicationScoped` annotation to `OTvisParser`.
3. Add `META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin`
   service descriptor with the single line
   `de.dlr.shepard.plugin.fileformat.thermography.OTvisParser`.
4. Add the module to `/opt/shepard/plugins/pom.xml` `<modules>` list.
5. Add the dependency to the backend `with-plugins` profile in
   `/opt/shepard/backend/pom.xml`.
6. Update `aidocs/34-upstream-upgrade-path.md` with the operator-facing
   row.

## Known pitfalls

- **Standalone build only** — running `mvn install` from this module
  publishes the artefact to your local `~/.m2/`, but the main backend
  build does not yet consume it. That is the wire-up follow-up's job.
- **No SPI binding yet** — even if the jar is dropped onto the backend
  classpath, `FileParserRegistry` will not find it until the local SPI
  shim is replaced with the canonical interface (see wire-up step 1).
- **No frame extraction** — uploads do not appear in the
  `ThermographyView` canvas yet; only the metadata table is populated.
  Tracked as `OTVIS-PARSE-2`.
