---
stage: feature-defined
last-stage-change: 2026-06-02
---

# `shepard-plugin-fileformat-svdx` — quickstart

**90-second task: "I have a `.svdx` from the TwinCAT Scope line and I
want to see what's in it."**

## Step 1 — Upload the file

Open your DataObject (e.g. the spot-welding test record), open the
file panel, drag-and-drop the `.svdx` (or use the upload dialog).
Shepard stores it as a FileReference like any other file.

## Step 2 — Trigger the parser

The plugin runs automatically when its backend dispatcher fires. In
the deployed instance the trigger is the standard "parse uploaded
file" flow that the file-format SPI binds (alongside
`fileformat-thermography` and `fileformat-robotics`); see
`install.md` for the operator surface to verify the parser is
registered.

If your instance has not yet wired the SPI dispatcher, the plugin
JAR is still useful as a library — invoke
`SvdxManifestParser.parse(ctx)` directly from any pipeline that has
the file bytes and an annotation sink.

## Step 3 — See what came out

On the FileReference detail page, the annotations panel will now
show entries under the `urn:shepard:svdx:*` namespace, including:

* the project Guid (so you can find every file from the same
  TwinCAT Scope session);
* the channel count and acquisition count (the difference between
  the two reveals trigger-group fan-out);
* the symbol names (`GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1`
  etc. — the same fully qualified TwinCAT symbol paths the OPC UA
  configurator would have used);
* the AmsNetId and TargetPort the data came from;
* a `companionCsv` pointer when a sibling `<basename>.csv` is in the
  same FileContainer.

You can filter your DataObject list by any of these (e.g. all files
where `urn:shepard:svdx:projectGuid = <guid>`) using the regular
annotation-search UI.

## Step 4 — Get sample data into a TimeseriesReference

The proprietary binary section of `.svdx` is not parsed on upload
(Beckhoff publishes no spec). To get sample-level data into a queryable
TimeseriesReference:

1. On the TwinCAT engineering box, run the TwinCAT Scope Export Tool
   ([Beckhoff Infosys](https://infosys.beckhoff.com/content/1033/te13xx_tc3_scopeview/1022949131.html))
   against your `.svdx` and export to CSV.
2. Upload the resulting CSV as a singleton FileReference on the **same
   DataObject** as the `.svdx` (`POST /v2/files`).
3. Create a `MAPPING_RECIPE` template targeting the
   `SvdxCsvIngestShape` IRI and bind the two appIds, then materialize
   it via `POST /v2/mappings/{templateAppId}/materialize`. The
   `SvdxCsvTransformExecutor` parses the CSV, writes each column into
   TimescaleDB, and returns the derived TimeseriesReference appId. See
   the **CSV ingest via MAPPING_RECIPE** section of `reference.md` for
   the exact request/response bodies.

   ```bash
   # body of the MAPPING_RECIPE template:
   #   { "templateKind": "MAPPING_RECIPE",
   #     "mappingRecipeShape": "http://semantics.dlr.de/shepard/transform#SvdxCsvIngestShape",
   #     "svdxFileReferenceAppId": "<svdx appId>",
   #     "csvFileReferenceAppId":  "<csv appId>",
   #     "targetDataObjectAppId":  "<DataObject appId>" }

   curl -X POST "https://<shepard-host>/v2/mappings/$TEMPLATE_APPID/materialize" \
     -H "X-API-KEY: $API_KEY" -H "Content-Type: application/json" \
     -d '{"inputReferenceAppIds":{"svdxFileAppId":"<svdx appId>","csvFileAppId":"<csv appId>"}}'
   ```

   The ingest is idempotent: re-materializing the same `.svdx`+`.csv`
   pair returns the existing reference. Downstream notebooks can also
   consume the raw CSV via `pytcs`
   ([PyPI](https://pypi.org/project/pytcs/)).

## Worked example — MFFD AFP spot-welding file

Smallest file in the campaign: `Scope Project_AutoSave_18_26_04.svdx`
(7.2 MB). After parsing you will see (excerpted):

```
urn:shepard:svdx:formatVersion       = 0x000000000c9671
urn:shepard:svdx:projectGuid         = 61ededc3-4d5a-4502-823a-263c661a692f
urn:shepard:svdx:projectName         = Scope Project
urn:shepard:svdx:dataPoolGuid        = ec7a812f-62f8-497d-9a0d-92f60425dd87
urn:shepard:svdx:mainServer          = 127.0.0.1.1.1
urn:shepard:svdx:autoSaveMode        = SVDX
urn:shepard:svdx:channelCount        = 46
urn:shepard:svdx:acquisitionCount    = 149
urn:shepard:svdx:amsNetId            = 169.254.165.182.1.1
urn:shepard:svdx:port                = 851
urn:shepard:svdx:dataType            = INT16
urn:shepard:svdx:dataType            = INT32
urn:shepard:svdx:dataType            = REAL32
urn:shepard:svdx:dataType            = REAL64
urn:shepard:svdx:dataType            = BIT
urn:shepard:svdx:dataType            = UINT64
urn:shepard:svdx:symbolName          = GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1
urn:shepard:svdx:symbolName          = RobotData.rRoboPosA
... (149 symbolName lines in total)
urn:shepard:svdx:channelName         = aTemperatureAnalogIntput1
... (46 channelName lines in total)
```

The full first-class metadata of an industrial welding-process
recording, queryable in Shepard the moment the file lands.

## Limits

* If the `.svdx` is corrupt past the envelope, only the
  `formatVersion` annotation is emitted; everything else is
  silently skipped (and logged as a `WARNING` in the backend log).
* If the file is not recognised as an SVDX (wrong magic in bytes
  9..11), no annotations are emitted at all.
* The plugin does not modify the file — the original bytes stay in
  storage; everything written goes to the Neo4j semantic annotation
  layer.
