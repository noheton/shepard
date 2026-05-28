# Reference — shepard-plugin-fileformat-thermography (tier-1)

**Status:** alpha · tier-1 metadata-only

Comprehensive reference for the Edevis OTvis tier-1 parser. Each
section answers a single question an operator or power user might ask
when something goes wrong.

## 1. What the plugin does

When a `.OTvis` file lands on a FileContainer, this plugin is invoked
via the `FileParserPlugin` SPI (see
`aidocs/integrations/110 §3`). It:

1. Opens the upload as a POSIX tar archive.
2. Extracts the `content.xml` entry (UTF-16 LE XML).
3. Reads the `<FileInfo>` block.
4. Parses the filename for the `S<n>_M<n>_L<n>_F<n>.OTvis` grid
   pattern.
5. Writes one `:SemanticAnnotation` per discovered field. Acquisition
   annotations are anchored on the FileReference; MFFD grid annotations
   are anchored on the parent DataObject.

The plugin creates no DataObjects, no DataContainers, and never writes
anywhere outside its `AnnotationWriter` callback — see the
DO-sprawl containment rule in `aidocs/integrations/114 §0`.

## 2. Accepted inputs

| Field | Value |
|---|---|
| File extension | `.OTvis` (case-insensitive) |
| MIME type | `application/x-tar` accepted; `application/octet-stream` accepted when extension matches |
| Archive shape | POSIX tar — typically 6–10 MB; must contain `content.xml` |

`.diproj` files are NOT accepted (deliberate — DO-sprawl rule).

## 3. Annotation key reference

### 3.1 `urn:shepard:thermography:*` (FileReference subject)

| Predicate | XML source | Unit / range | Example |
|---|---|---|---|
| `urn:shepard:thermography:frameRate_Hz` | `<FrameRate>` (Hz stripped) | float Hz | `"30"` |
| `urn:shepard:thermography:integrationTime_s` | `<IntegrationTime>` (s stripped) | float seconds | `"0.007"` |
| `urn:shepard:thermography:excitationDevice` | `<ExcitationDeviceSelection>` (canonicalised) | `"halogen" \| "flash" \| "ultrasound" \| "passive"` | `"halogen"` |
| `urn:shepard:thermography:excitationFrequency_Hz` | `<ExcitationFrequency>` (Hz stripped) | float Hz | `"0.015"` |
| `urn:shepard:thermography:excitationAmplitude_pct` | `<ExcitationAmplitude>` (% stripped) | float percent | `"70.00"` |
| `urn:shepard:thermography:excitationSignalType` | `<ExcitationSignalType>` (lower-cased) | `"sine" \| "square" \| ...` | `"sine"` |
| `urn:shepard:thermography:recordingType` | `<RecordingType>` (lower-cased) | `"evaluation" \| "raw" \| ...` | `"evaluation"` |
| `urn:shepard:thermography:resolution` | `<Window>` (W,H projected) | `"<W>x<H>"` | `"1024x768"` |
| `urn:shepard:thermography:conditioningPeriods` | `<ConditionPeriods>` | int | `"1"` |
| `urn:shepard:thermography:acquisitionPeriods` | `<AcquisitionPeriods>` | int | `"2"` |
| `urn:shepard:thermography:campaign` | `<Campaign>` | string | `"MFFD"` |
| `urn:shepard:thermography:moduleName` | `<ModuleName>` | string | `"OTvis"` |
| `urn:shepard:thermography:creatingVersion` | `<CreatingVersion>` | string (Edevis software version) | `"7.0.425.8903"` |
| `urn:shepard:thermography:createdAt` | `<CreationDate>` (ISO-8601 normalised) | ISO-8601 UTC | `"2023-07-02T06:55:41.414Z"` |

### 3.2 `urn:shepard:mffd:*` (parent DataObject subject)

Emitted only when the filename matches the canonical
`S<n>_M<n>_L<n>_F<n>.OTvis` pattern.

| Predicate | Source | Example |
|---|---|---|
| `urn:shepard:mffd:section` | filename group 1 (literal `S<n>`) | `"S4"` |
| `urn:shepard:mffd:module`  | filename group 2 (literal `M<n>`) | `"M13"` |
| `urn:shepard:mffd:layer`   | filename group 3 (literal `L<n>`) | `"L18"` |
| `urn:shepard:mffd:frame`   | filename group 4 (literal `F<n>`) | `"F4"` |

## 4. Behaviour on partial / malformed input

The parser is best-effort by contract — it never throws on a
malformed file:

| Situation | Behaviour |
|---|---|
| Corrupt tar archive | Grid annotations from filename still emit; acquisition annotations skipped. |
| Missing `content.xml` | Grid annotations from filename still emit; acquisition annotations skipped. |
| Malformed XML | Grid annotations from filename still emit; acquisition annotations skipped. |
| Missing XML field | The matching annotation is silently dropped; other fields still emit. |
| Unrecognised excitation-device wording | Falls through as lower-cased original (e.g. `"laser diode stack"`). |
| Filename does not match `S_M_L_F` pattern | All four MFFD grid annotations are dropped; acquisition annotations still emit. |
| Both DataObject and FileReference appIds absent | Zero annotations emitted (nothing to anchor on). |

## 5. Out-of-scope (tier-2+)

| Concern | Filed as |
|---|---|
| Frame extraction from `sequence0/f0.bin` (raw IR frames) | `OTVIS-PARSE-2` |
| Frame extraction from `sequence1/*` (lock-in amplitude/phase) | `OTVIS-PARSE-2` |
| OME-Zarr / NeXus FAIR-intermediate output | `OTVIS-PARSE-2` |
| Frontend channel-bound playback | `THERMO-CHANNELS-1` |
| Full Three.js IR-sequence viewer | `OTVIS-VIEW-1` |
| `.diproj` project-manifest support | Not planned (DO-sprawl containment) |

## 6. SPI surface

```java
package de.dlr.shepard.plugin.fileformat.thermography;

public interface FileParserPlugin {
    boolean accepts(String mimeType, String filename);
    int parse(ParseContext ctx);

    interface ParseContext {
        byte[] bytes();
        String filename();
        Optional<String> parentDataObjectAppId();
        Optional<String> fileReferenceAppId();
        AnnotationWriter annotations();
    }

    @FunctionalInterface
    interface AnnotationWriter {
        void write(String subjectAppId, String predicate, String value);
    }
}
```

Implementation: `OTvisParser.accepts(...)` matches on extension
`.OTvis` (case-insensitive) or MIME type `application/x-tar`.

## 7. Compose / install footprint

No sidecars, no external services, no compose changes required for
tier-1. The plugin runs entirely inside the backend JVM. See
`docs/install.md` for the standalone-build status.
