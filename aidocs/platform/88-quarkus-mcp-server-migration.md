# 88 — Native Quarkus MCP Server: Replacing the Python Sidecar

**Status:** Design  
**Author:** design session 2026-05-21  
**Depends on:** L2d (appId-first v2 surface), aidocs/30 (MCP tool inventory), aidocs/86 (AI plugin / `LlmProvider` SPI), aidocs/69 (runtime CDI plugin indexing — PM1b3), TS-IDa (timeseries appId backfill, aidocs/87)  
**Supersedes:** aidocs/30 §6 (Python sidecar deployment shape)  
**Blast radius:** Medium — touches backend pom, routing config; no v2 API surface changes

---

## 1. Problem: Python Sidecar Limitations

The current MCP integration (`shepard-plugin-mcp`) is a FastMCP Python process that
proxies all tool calls through the public Shepard REST API. This creates structural
friction that compounds as the tool inventory grows:

**HTTP round-trip on every call.** `list_collections` → Python sidecar → `GET /v2/collections`
→ backend → sidecar → MCP client. Every tool call adds one full serialization cycle
(Neo4j → Java entity → JSON → network → JSON → Python dict → MCP response). Tools that
chain multiple operations (e.g. `get_data_object` fetching container summaries) pay N
round-trips.

**Two deployment artefacts, two health surfaces.** The sidecar has its own Docker image,
its own `docker-compose` service, its own crash/restart behaviour. On cold start, the
sidecar may come up before the backend is accepting traffic — tools return 503 until the
backend's `/q/health/ready` gate opens, with no retry logic.

**Bearer token relay.** The sidecar must receive, validate (enough to forward), and pass
through the caller's OIDC bearer token on every downstream call. Any token expiry or
scope mismatch produces an error path the Python code must interpret from the Shepard
JSON error envelope — not from a typed exception.

**Diverging dependency trees.** Backend evolves on Java 21 / Quarkus 3.x; the sidecar
carries its own Python + FastMCP versions, its own `requests` / `httpx` pinning, its own
CVE surface (Trivy scans both images separately). Breaking changes to the v2 API IO
shapes are caught only at runtime when the sidecar calls the new endpoint; there is no
compile-time contract.

**No access to internal service layer.** A tool that needs, say, the full container
summary breakdown (the fix for the `referenceIds` confusion described in aidocs/30 §4)
must call multiple REST endpoints and join the results in Python. The same logic already
exists in `DataObjectService` / `TimeseriesContainerService` — it just isn't reachable
from outside the JVM.

---

## 2. Solution: Native Quarkus MCP Tools

Add `io.quarkiverse.mcp:quarkus-mcp-server-http:1.12.0` to `backend/pom.xml`. Tools
become `@ApplicationScoped` CDI beans annotated with `@Tool` from the extension. They
inject existing Shepard services directly — no HTTP hop, no token relay, no separate
process.

```xml
<!-- backend/pom.xml — inside <dependencies> -->
<dependency>
  <groupId>io.quarkiverse.mcp</groupId>
  <artifactId>quarkus-mcp-server-http</artifactId>
  <version>1.12.0</version>
</dependency>
```

The extension registers an SSE endpoint at `/mcp/sse` by default (configurable via
`quarkus.mcp.server.http.root-path`). The Zoraxy rule that currently forwards
`shepard.nuclide.systems/mcp` → sidecar is unchanged in structure; only the upstream
target changes (§6).

Auth is not a new concern: `quarkus-oidc` already secures all `/v2/` paths.
Configuring `quarkus.http.auth.permission.mcp.paths=/mcp/*` with policy `authenticated`
applies the same OIDC bearer gate to the MCP endpoint. The Keycloak `mcp-client` PKCE
client and the SSE URL at `shepard.nuclide.systems/mcp/sse` remain unchanged for the
Claude remote connector config (see aidocs/30 §6 for the full connector JSON).

**Open question (Phase 1 blocker):** Verify that `quarkus-mcp-server-http` cooperates
with `quarkus-oidc` on long-lived SSE connections (token refresh mid-stream). If the
extension does not natively delegate to Quarkus's security layer, an explicit
`@RolesAllowed("**")` / filter may be needed. Flag during Phase 1 implementation.

Tools call the service layer, not the REST layer:

```
MCP client → SSE /mcp/sse → Quarkus (quarkus-mcp-server-http)
                                 │
                    @Tool CDI beans (de.dlr.shepard.v2.mcp.*)
                                 │
                    CollectionService / DataObjectService /
                    TimeseriesContainerService / ...
                                 │
                          Neo4j / TimescaleDB
```

---

## 3. Tool Inventory Migration

Full tool inventory is defined in aidocs/30 §3. This section maps each group to its
Java package shape and identifies the services each tool class injects.

### Package layout

```
de.dlr.shepard.v2.mcp/
  CollectionMcpTools.java        — list_collections, get_collection
  DataObjectMcpTools.java        — get_data_object, list_data_objects, search_data_objects,
                                   set_predecessor, get_predecessor_chain, get_children
  TimeseriesMcpTools.java        — list_timeseries_containers, list_channels,
                                   get_channel_data, get_channel_summary, compare_channels
  FileMcpTools.java              — list_file_containers, list_files, get_file_text
  AnnotationMcpTools.java        — list_annotations, create_annotation, search_ontology_classes
  LabJournalMcpTools.java        — list_lab_journal, get_lab_journal_entry, create_lab_journal_entry
  ImportMcpTools.java            — get_import_context, validate_import, get_import_plan
  InstanceMcpTools.java          — describe_instance, list_tools
```

### `list_collections` stub

```java
package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.common.util.QueryParamHelper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class CollectionMcpTools {

    @Inject
    CollectionService collectionService;

    @Tool(description = """
        List all collections the caller has read access to.
        Returns appId, name, description and dataObjectCount.
        Use collectionAppId from the results to call get_data_object or list_data_objects.
        """)
    public List<CollectionIO> list_collections(
            @ToolArg(description = "Page index, 0-based.", required = false) Integer page,
            @ToolArg(description = "Page size (default 20, max 100).", required = false) Integer size,
            @ToolArg(description = "Filter by name substring (case-insensitive).", required = false) String name) {

        int safePage = page != null ? Math.max(page, 0) : 0;
        int safeSize = size != null ? Math.min(Math.max(size, 1), 100) : 20;
        var params = new QueryParamHelper().withPageAndSize(safePage, safeSize);
        if (name != null) params = params.withName(name);
        // signature per CollectionService.getAllCollections — returns page-aware list
        return collectionService.getAllCollections(params).stream()
                .map(CollectionIO::new)
                .toList();
    }
}
```

### `get_data_object` stub

Returns the container-breakdown shape from aidocs/30 §4, not the raw `referenceIds`
that caused the TR-004 404 failures.

```java
package de.dlr.shepard.v2.mcp;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.dataobject.io.DataObjectDetailV2IO;
import de.dlr.shepard.v2.mcp.io.DataObjectMcpIO;   // see note below
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DataObjectMcpTools {

    @Inject DataObjectService dataObjectService;
    @Inject TimeseriesContainerService timeseriesContainerService;
    @Inject EntityIdResolver idResolver;

    @Tool(description = """
        Fetch a DataObject by its appId (UUID v7).
        The response breaks containers out by kind (timeseries, files, structuredData)
        so you can immediately call list_channels(containerAppId) or list_files(containerAppId).
        predecessors/successors/children are included as {appId, name} stubs —
        call get_data_object on each to traverse the provenance chain.
        NOTE: the raw referenceIds field is intentionally omitted; use containers instead.
        """)
    public DataObjectMcpIO get_data_object(
            @ToolArg(description = "UUID v7 appId of the DataObject.") String dataObjectAppId) {

        long ogmId = idResolver.resolveDataObjectOgmId(dataObjectAppId);
        // DataObjectService.getDataObject(long) is the single-arg overload at line 130;
        // it delegates to getDataObject(shepardId, null) — no collection context needed.
        DataObject entity = dataObjectService.getDataObject(ogmId);
        // DataObjectMcpIO assembles the containers breakdown from entity relationships;
        // it does NOT copy referenceIds into the response (per aidocs/30 §4 fix).
        return DataObjectMcpIO.from(entity, timeseriesContainerService);
    }
}
```

`DataObjectMcpIO` is a dedicated response class in `de.dlr.shepard.v2.mcp.io` — it
does not reuse `DataObjectDetailV2IO` because the MCP shape intentionally omits
`referenceIds` and adds the `containers` breakdown. Keep the two shapes separate.

### `get_timeseries_data` stub

Today the channel is addressed by the 5-tuple (`measurement`, `device`, `location`,
`symbolicName`, `field`). The tool accepts all five; only `measurement` is required
(matching `TimeseriesLiveWindowRest` behaviour — non-null fields are ANDed). Once
TS-IDa ships (aidocs/87), a single `timeseriesAppId` param will be preferred; update
the tool signature at that point and keep the 5-tuple params as fallback with a
deprecation note in the description.

```java
@Tool(description = """
    Fetch historical data for a single timeseries channel.
    Identify the channel by timeseriesAppId (preferred, post-TS-IDa) OR by the
    5-tuple (measurement required; device/location/symbolicName/field optional).
    Returns up to maxPoints samples with LTTB downsampling applied for large ranges.
    For anomaly investigation, pair with get_channel_summary to see min/max/std first.
    """)
public List<TimeseriesPointIO> get_channel_data(
        @ToolArg(description = "Container appId (UUID v7).") String containerAppId,
        @ToolArg(description = "Channel appId (UUID v7) — preferred post-TS-IDa.", required = false) String timeseriesAppId,
        @ToolArg(description = "Measurement name (required if timeseriesAppId absent).", required = false) String measurement,
        @ToolArg(description = "Field name.", required = false) String field,
        @ToolArg(description = "Device filter.", required = false) String device,
        @ToolArg(description = "Location filter.", required = false) String location,
        @ToolArg(description = "Symbolic name filter.", required = false) String symbolicName,
        @ToolArg(description = "ISO-8601 start time.", required = false) String startTime,
        @ToolArg(description = "ISO-8601 end time.", required = false) String endTime,
        @ToolArg(description = "Maximum data points (default 1000, LTTB applied).", required = false) Integer maxPoints) {
    // ... resolve channel, delegate to TimeseriesService.getData()
}
```

---

## 4. Relationship to `shepard-plugin-ai`

MCP tools in core backend can optionally delegate to the `LlmProvider` SPI from
`shepard-plugin-ai` (aidocs/86). Example: a `summarize_data_object` tool that calls
`llmProvider.complete(AiCapability.TEXT, request)` to produce a narrative from the
DataObject's attributes and lab journal entries.

Dependency arrow:

```
mcp-tools (core backend)  ──uses──▶  LlmProvider SPI (interface in shepard-plugin-ai)
                                              ▲
                                    implemented by shepard-plugin-ai at runtime
```

Tools that use `LlmProvider` must compile without the AI plugin present. Use CDI
`Instance<LlmProvider>` injection with an `isUnsatisfied()` guard:

```java
@Inject
Instance<LlmProvider> llmProvider;

// inside tool method:
if (llmProvider.isUnsatisfied()) {
    return Map.of("summary", "AI plugin not configured — install shepard-plugin-ai.");
}
LlmResponse r = llmProvider.get().complete(request);
```

Tools with no LLM use (all tools in Phase 1) have no dependency on the AI plugin at
all. The `Instance<LlmProvider>` pattern is only needed for AI-augmented tools added
in later phases.

### Plugin-contributed `@Tool` beans

Once PM1b3 (aidocs/69) ships true runtime CDI indexing, a `shepard-plugin-*` JAR can
contribute its own `@ApplicationScoped @Tool` beans. The Quarkiverse extension
auto-discovers all `@Tool` beans in the CDI container regardless of which module
declared them. Until PM1b3 lands, plugin tools must be added to
`quarkus.index-dependency.shepard-plugin-foo.*` in `application.properties` and
declared as a `<dependency>` in the `with-plugins` Maven profile — same constraint as
all other plugin CDI beans today (see aidocs/69 §1 for the full description of this
gap). Phase 3 decommission of the Python sidecar **does not depend on PM1b3**; core
tools are in-tree and are indexed at build time.

---

## 5. Migration Plan

### Phase 1 — Parallel operation (current sprint)

1. Add `quarkus-mcp-server-http:1.12.0` to `backend/pom.xml`.
2. Implement three read-only tools in `de.dlr.shepard.v2.mcp`:
   - `list_collections` (CollectionMcpTools)
   - `get_data_object` with container breakdown (DataObjectMcpTools)
   - `list_timeseries_containers` + `list_channels` (TimeseriesMcpTools)
3. During Phase 1 the Quarkus MCP endpoint lives at `/v2/mcp/sse` (set via
   `quarkus.mcp.server.http.root-path=/v2/mcp`). The Python sidecar continues to
   serve the live `/mcp/sse` Zoraxy target. This avoids a path collision while both
   are running.
4. Validate auth wiring: confirm `quarkus-oidc` bearer gate applies to `/v2/mcp/*`
   without special configuration (or add the `permission` block if not).
5. Smoke-test against the LUMEN demo: `get_data_object(tr004AppId)` must return
   `containers.timeseries[0].containerAppId` (not `referenceIds`).

### Phase 2 — Port remaining tools, flip Zoraxy

1. Port all remaining tools from aidocs/30 §3 (Files, Annotations, Lab Journal, Import,
   Instance).
2. Test with the Claude remote connector pointed at `/v2/mcp/sse`. Run the full LUMEN
   TR-004 investigation scenario end-to-end (aidocs/30 §8 test plan).
3. When tests pass: update `quarkus.mcp.server.http.root-path=/mcp`. Update the Zoraxy
   virtual directory rule upstream target from `localhost:8811` to `localhost:8080`.
   The public SSE URL `shepard.nuclide.systems/mcp/sse` is unchanged for all callers.
4. The Python sidecar is now dark (Zoraxy no longer routes to it) but still running.

### Phase 3 — Decommission Python sidecar

1. Remove `shepard-mcp` service from `infrastructure/docker-compose.override.yml`.
2. Remove `plugins/shepard-plugin-mcp/` directory (or archive as `_archived/`).
3. Update aidocs/30 status from `Design` to `Superseded by aidocs/88`.
4. Update `infrastructure/proxy/Caddyfile` / Zoraxy docs to reflect new upstream.
5. Update aidocs/34 upgrade-path tracker with the deployment change.

---

## 6. SSE Endpoint and Zoraxy Config

| Phase | Quarkus path | Zoraxy target | Public URL |
|---|---|---|---|
| Phase 1 (parallel) | `/v2/mcp/sse` | `localhost:8811` (sidecar unchanged) | `shepard.nuclide.systems/mcp/sse` |
| Phase 2–3 (cutover) | `/mcp/sse` | `localhost:8080` (backend) | `shepard.nuclide.systems/mcp/sse` |

Zoraxy virtual directory rule change (Phase 2):

| Field | Before | After |
|---|---|---|
| Virtual path | `/mcp` | `/mcp` (unchanged) |
| Target upstream | `localhost:8811` | `localhost:8080` |
| Strip path prefix | ✓ | ✓ (unchanged) |

The Claude remote connector JSON in operator docs does not change.

---

## 7. Trade-offs and Risks

**Extension maturity.** `quarkus-mcp-server-http` is v1.x (Quarkiverse, not Quarkus
core). The MCP spec itself is still evolving (2025-05-22 spec is current). Pin the
version in `backend/pom.xml` and review release notes on each Quarkus BOM bump. Open
issue: confirm the extension's SSE implementation handles client reconnects correctly
for long-lived Claude conversations.

**Tool bugs affect backend stability.** A `@Tool` bean that blocks indefinitely or
throws an uncaught exception runs in the backend's thread pool. The Python sidecar
crashed in isolation; a buggy native tool can degrade backend response times. Mitigation:
annotate tool methods with `@Timeout` (MicroProfile Fault Tolerance) and ensure all
tool methods are non-transactional (`@Transactional` must not wrap tool handlers —
let services manage their own transaction boundaries).

**5-tuple friction until TS-IDa.** `get_channel_data` requires up to 5 params to
identify a channel. This is identical friction to the current REST surface — migrating
to native tools does not make it worse, but it does not fix it either. The fix is
TS-IDa (aidocs/87); the tool signature should be updated in the same PR that backfills
`timeseriesAppId` on existing channels.

**CDI plugin tool contribution blocked on PM1b3.** Plugin-contributed `@Tool` beans
from drop-in JARs require runtime CDI indexing (aidocs/69 PM1b3). Until then, plugin
tools must be in-tree dependencies. The Python plugin SPI (`mcp_tools.py` in
aidocs/30 §5) goes away entirely with the sidecar; plugin authors migrating tools must
either ship them as in-tree Maven dependencies or wait for PM1b3. Call this out in
the Phase 3 release note.

**Upside.** Tools get full access to Quarkus caches, internal service state, and
transaction context. `get_data_object` assembles the container breakdown in a single
Neo4j traversal — no N+1 REST calls. Boot ordering is eliminated: tools become
available exactly when the backend declares ready.
