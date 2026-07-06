package de.dlr.shepard.v2.dataobject.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.v2.dataobject.io.CreateDataObjectV2IO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import de.dlr.shepard.v2.dataobject.io.DataObjectDetailV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectListFieldFilter;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import de.dlr.shepard.v2.dataobject.io.DataObjectSummaryIO;
import de.dlr.shepard.v2.dataobject.io.PredecessorEdgePatchIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.m4i.M4iDataObjectRenderer;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * L2d Phase A.2 — {@code /v2/collections/{collectionAppId}/data-objects}.
 * Mirrors {@link de.dlr.shepard.v2.collection.resources.CollectionV2Rest}
 * for the {@code DataObject} entity. Both shelves hit the same
 * {@link DataObjectService}; this resource translates {@code appId →
 * ogmId} at the boundary via {@link EntityIdResolver}.
 *
 * <p>Hierarchy follows the v1 shape: {@code DataObject}s live under a
 * specific {@code Collection}, so list / create are keyed by
 * {@code collectionAppId}.
 *
 * <p><b>Scope (Phase A.2 + BUG-COLL-APPID-ROUTE-006-V2-LIST).</b> Core CRUD
 * plus appId-native list filters: list / get / create / RFC-7396 merge-patch /
 * delete. Deferred:
 * <ul>
 *   <li>{@code versionUID} query parameter — versioned reads land with
 *       the v2 snapshot work.</li>
 *   <li>Permissions / roles — covered by a dedicated
 *       {@code /v2/permissions/{appId}} resource (Phase C).</li>
 * </ul>
 *
 * <p><b>Auth.</b> Read on the parent Collection to list; Read on the
 * DataObject to get; Write on the DataObject to PATCH or DELETE; Write
 * on the parent Collection to create. Permissions for an existing
 * DataObject are inherited from its Collection in shepard's model, so
 * the gate at the DataObject level is just the inherited check.
 */
@Path("/v2/collections/{collectionAppId}/data-objects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "DataObjects")
public class DataObjectV2Rest {

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/data-objects.bad-request";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/data-objects.not-found";
  private static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/data-objects.unauthorized";
  private static final String PROBLEM_TYPE_FORBIDDEN = "/problems/data-objects.forbidden";
  private static final String PROBLEM_TYPE_INTERNAL = "/problems/data-objects.internal";

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  Validator validator;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  M4iDataObjectRenderer m4iDataObjectRenderer;

  @GET
  @Operation(
    operationId = "listDataObjects",
    summary = "List DataObjects under a Collection.",
    description =
      "Returns a page of `:DataObject` entities belonging to the Collection " +
      "identified by `collectionAppId`. Each row in the response carries the full " +
      "`DataObjectIO` fields plus three per-kind reference counts: " +
      "`timeseriesCount`, `fileCount`, `structuredDataCount`. Counts reflect " +
      "non-deleted references only and are computed in a single Cypher round-trip " +
      "(no N+1 queries).\n\n" +
      "Pagination: omit `page` / `pageSize` to get the first 50; supply both to " +
      "paginate. `pageSize` must be between 1 and 200 inclusive (400 on violation).\n\n" +
      "**Pagination envelope (diverges from other v2 list endpoints):** This endpoint " +
      "returns a plain JSON array in the response body. Pagination metadata is carried " +
      "in two response headers instead of a `PagedResponseIO` body envelope:\n\n" +
      "- `Content-Range: dataobjects START-END/TOTAL` — zero-based index of the first " +
      "and last returned item followed by the total match count " +
      "(e.g. `dataobjects 0-49/8514`). Follows RFC 7233 §4.2 extension syntax.\n" +
      "- `X-Total-Count: TOTAL` — bare integer total for callers that only need the count.\n\n" +
      "All other paginated `/v2/` list endpoints return a `PagedResponseIO` body " +
      "envelope (`{items, total, page, pageSize}`). This endpoint predates that " +
      "convention and retains header-based metadata for backwards compatibility. " +
      "Callers (including `useListDataObjects.ts`) must read these headers; the body " +
      "envelope is absent. Migration to `PagedResponseIO` is tracked as " +
      "`APISIMP-DO-LIST-CONTENT-RANGE` in the backlog (a coordinated backend + " +
      "frontend two-commit change).\n\n" +
      "Filtering: `name` does a case-insensitive substring match.\n\n" +
      "Optional enrichment via `?include=time-bounds`: adds `timeBoundsStart` and " +
      "`timeBoundsEnd` (epoch nanoseconds) to each item, reflecting the earliest and " +
      "latest data-point timestamps across all timeseries channels. Null on items " +
      "with no timeseries data. Omitted from the response entirely when " +
      "`?include=time-bounds` is not requested.\n\n" +
      "**Payload diet (DB-OPT5).** By default the list response drops fields the " +
      "collection-detail UI never reads — `description`, `attributes`, and the three " +
      "deprecated `int` count siblings (`timeseriesReferenceCount`, `fileBundleCount`, " +
      "`structuredDataReferenceCount`). Use `?include=full` to opt back into the full " +
      "wire shape (transitional safety valve until a breaking-version bump can drop " +
      "the deprecated ints unconditionally). For finer-grained control, pass " +
      "`?fields=appId,name,createdAt,...` (flat CSV of field names; GitHub REST " +
      "convention). `id`, `appId`, and `name` are always included as resource " +
      "identity. Unknown field names return 400 with the offending name in the body. " +
      "Dotted-path nested selection (e.g. `attributes.bench`) is not supported in this " +
      "iteration — see DB-OPT5-NESTED in the backlog.\n\n" +
      "Auth: Read on the parent Collection. DataObjects inherit Collection " +
      "permissions; there is no per-DO permission gate.\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` " +
      "to fetch a specific DataObject with full reference detail.",
    extensions = @Extension(name = "x-agent-hint", value = "Returns paginated list. Use ?include=time-bounds for timeseries coverage bars. timeseriesCount/fileCount/structuredDataCount give reference counts per kind. Default payload drops description/attributes; use ?include=full or ?fields= for finer control.")
  )
  @APIResponse(
    responseCode = "200",
    description =
      "DataObject page (may be empty). Each item includes `timeseriesCount`, `fileCount`, " +
      "`structuredDataCount`. When `?include=time-bounds` is set, `timeBoundsStart`/`timeBoundsEnd` " +
      "are also populated (null means no data yet). Use `?status=READY` (or DRAFT, IN_REVIEW, " +
      "PUBLISHED, ARCHIVED, FAILED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED) to filter server-side by lifecycle status.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectListItemV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @Parameter(description = "Optional display-name filter (case-insensitive substring match). Omit to return all DataObjects.")
    @QueryParam(Constants.QP_NAME) String name,
    @Parameter(description = "Lifecycle status filter. Accepted values: DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED, FAILED, NCR_OPEN, ON_HOLD, REJECTED, CERTIFIED. Unrecognised values silently return an empty page (no 400).")
    @QueryParam("status") String status,
    @Parameter(description = "Zero-based page index (default 0). Negative values are clamped to 0.")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size (default 50). Must be between 1 and 200 inclusive (400 otherwise).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Parameter(description = "Comma-separated response modifiers. `time-bounds` — populates `timeBoundsStart`/`timeBoundsEnd` per DataObject (costs one extra TimescaleDB round-trip). `full` — opts back into the full (pre-DB-OPT5) wire shape including deprecated fields.")
    @QueryParam("include") String include,
    @Parameter(description = "Comma-separated field projection (flat CSV). Fields must exist on DataObjectListItemV2; an unrecognised field returns 400 with the offending name in the body. `id` and `appId` are always included. Omit to return all default fields.")
    @QueryParam("fields") String fields,
    @Parameter(description = "Annotation filter: `<predicateIri>=<value>`. Restricts results to DataObjects carrying a semantic annotation with exactly that predicate IRI and value. Example: `urn:shepard:quality:rating=PASS`. Malformed values (missing `=` or empty parts) are silently ignored.")
    @QueryParam("annotationFilter") String annotationFilter,
    @Parameter(description = "COLL-TIMELINE-DRILLDOWN-FILTER-1 — inclusive lower bound (ISO-8601 instant, e.g. `2024-06-02T00:00:00Z`). Restricts results to DataObjects whose `createdAt` is >= this value. Pair with `createdBefore` for a window. Malformed values are silently ignored.")
    @QueryParam("createdAfter") String createdAfter,
    @Parameter(description = "COLL-TIMELINE-DRILLDOWN-FILTER-1 — exclusive upper bound (ISO-8601 instant, e.g. `2024-06-03T00:00:00Z`). Restricts results to DataObjects whose `createdAt` is < this value. Pair with `createdAfter` for a window. Malformed values are silently ignored.")
    @QueryParam("createdBefore") String createdBefore,
    @Parameter(description = "SIDEBAR-LAZY-TREE — restrict to the direct, non-deleted children of the DataObject with this `appId` (within the same Collection). Drives the lazy collection-sidebar tree: fetch one hierarchy level per expand. Composes with `page`/`pageSize`/`fields`/`name`/`status`/`annotationFilter`. Mutually exclusive with `topLevel=true` (when both are given, `topLevel` wins). An unknown `parentAppId` yields an empty page.")
    @QueryParam("parentAppId") String parentAppId,
    @Parameter(description = "SIDEBAR-LAZY-TREE — when `true`, return ONLY root DataObjects (those with no parent DataObject inside the Collection). Drives the lazy collection-sidebar tree's initial load. Composes with the other filters. Overrides `parentAppId` when both are set.")
    @QueryParam("topLevel") Boolean topLevel,
    @Parameter(description = "BUG-COLL-APPID-ROUTE-006-V2-LIST — restrict to DataObjects that are direct successors of the DataObject with this `appId` (within the same Collection). Drives successor navigation: `?predecessorAppId=X` returns all DataObjects for which X is a predecessor. Composes with `page`/`pageSize`/`name`/`status`/`annotationFilter`. An unknown `predecessorAppId` yields an empty page.")
    @QueryParam("predecessorAppId") String predecessorAppId,
    @Parameter(description = "BUG-COLL-APPID-ROUTE-006-V2-LIST — restrict to DataObjects that are direct predecessors of the DataObject with this `appId` (within the same Collection). Drives predecessor navigation: `?successorAppId=Y` returns all DataObjects for which Y is a successor. Composes with `page`/`pageSize`/`name`/`status`/`annotationFilter`. An unknown `successorAppId` yields an empty page.")
    @QueryParam("successorAppId") String successorAppId,
    @Context SecurityContext sc
  ) {
    // DB-OPT5: validate ?fields= early so a bad query returns 400 before any DB hit.
    if (fields != null && !fields.isBlank()) {
      String unknown = DataObjectListFieldFilter.firstUnknownField(fields);
      if (unknown != null) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ProblemJson(
            "/problems/data-objects.unknown-field",
            "Unknown field in ?fields= query parameter",
            400,
            "Field '" + unknown + "' does not exist on DataObjectListItemV2.",
            null
          ))
          .type("application/problem+json")
          .build();
      }
    }
    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection found for collectionAppId");

    Response gate = enforceAccess(collectionOgmId, AccessType.Read, sc);
    if (gate != null) return gate;

    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (status != null) params = params.withStatus(status);
    if (annotationFilter != null) params = params.withAnnotationFilter(annotationFilter);
    if (createdAfter != null || createdBefore != null) params = params.withCreatedRange(createdAfter, createdBefore);

    // SIDEBAR-LAZY-TREE — root / parent hierarchy filter. `topLevel=true` wins
    // over `parentAppId` when both are supplied. The parentAppId is resolved to
    // the parent's shepardId here at the boundary; the DAO's existing parentId
    // Cypher path (`<-[:has_child]-(parent {shepardId})`) does the restriction.
    if (Boolean.TRUE.equals(topLevel)) {
      params = params.withTopLevelOnly();
    } else if (parentAppId != null && !parentAppId.isBlank()) {
      DataObject parent = dataObjectDAO.findByAppId(parentAppId);
      if (parent == null || parent.isDeleted() || parent.getShepardId() == null) {
        // Unknown / deleted parent → empty page (mirrors an unmatched filter).
        return Response.ok("[]", MediaType.APPLICATION_JSON)
          .header("Content-Range", "dataobjects */0")
          .header("X-Total-Count", 0)
          .build();
      }
      params = params.withParentShepardId(parent.getShepardId());
    }

    // BUG-COLL-APPID-ROUTE-006-V2-LIST — predecessor / successor appId filters.
    if (predecessorAppId != null && !predecessorAppId.isBlank()) {
      DataObject predecessor = dataObjectDAO.findByAppId(predecessorAppId);
      if (predecessor == null || predecessor.isDeleted() || predecessor.getShepardId() == null) {
        return Response.ok("[]", MediaType.APPLICATION_JSON)
          .header("Content-Range", "dataobjects */0")
          .header("X-Total-Count", 0)
          .build();
      }
      params = params.withPredecessorShepardId(predecessor.getShepardId());
    }
    if (successorAppId != null && !successorAppId.isBlank()) {
      DataObject successor = dataObjectDAO.findByAppId(successorAppId);
      if (successor == null || successor.isDeleted() || successor.getShepardId() == null) {
        return Response.ok("[]", MediaType.APPLICATION_JSON)
          .header("Content-Range", "dataobjects */0")
          .header("X-Total-Count", 0)
          .build();
      }
      params = params.withSuccessorShepardId(successor.getShepardId());
    }

    params = params.withPageAndSize(page, pageSize);

    var dataObjects = dataObjectService.getAllDataObjectsByShepardIds(collectionOgmId, params, null);

    // Collect appIds for the batch count query (one round-trip for the whole page).
    List<String> appIds = new ArrayList<>(dataObjects.size());
    for (var d : dataObjects) {
      if (d.getAppId() != null) appIds.add(d.getAppId());
    }
    Map<String, long[]> counts = dataObjectDAO.findRefCountsByAppIds(appIds);

    // Optional: per-DataObject time bounds from TimescaleDB.
    // Two extra round-trips (one Cypher + one SQL) only when ?include=time-bounds.
    boolean includeTimeBounds = include != null && include.contains("time-bounds");
    // DB-OPT5: ?include=full opts back into the pre-diet wire shape.
    boolean includeFull = include != null && include.contains("full");
    Map<String, List<Long>> doToContainerIds = Collections.emptyMap();
    Map<Long, long[]> containerTimeBounds = Collections.emptyMap();
    if (includeTimeBounds && !appIds.isEmpty()) {
      doToContainerIds = dataObjectDAO.findTsContainerIdsByDataObjectAppIds(appIds);
      List<Long> allContainerIds = doToContainerIds.values().stream()
        .flatMap(List::stream)
        .distinct()
        .collect(Collectors.toList());
      if (!allContainerIds.isEmpty()) {
        containerTimeBounds = timeseriesDataPointRepository.findTimeBoundsByContainerIds(allContainerIds);
      }
    }

    var result = new ArrayList<DataObjectListItemV2IO>(dataObjects.size());
    for (var d : dataObjects) {
      long[] c = d.getAppId() != null ? counts.getOrDefault(d.getAppId(), new long[] { 0, 0, 0 }) : new long[] { 0, 0, 0 };
      var item = new DataObjectListItemV2IO(d, c[0], c[1], c[2]);
      if (includeTimeBounds && d.getAppId() != null) {
        List<Long> cIds = doToContainerIds.getOrDefault(d.getAppId(), Collections.emptyList());
        Long minNs = null;
        Long maxNs = null;
        for (Long cId : cIds) {
          long[] bounds = containerTimeBounds.get(cId);
          if (bounds != null) {
            if (minNs == null || bounds[0] < minNs) minNs = bounds[0];
            if (maxNs == null || bounds[1] > maxNs) maxNs = bounds[1];
          }
        }
        item.setTimeBoundsStart(minNs);
        item.setTimeBoundsEnd(maxNs);
      }
      result.add(item);
    }

    // Count total matching DataObjects for Content-Range / X-Total-Count headers.
    // Use the same params (minus pagination) so filters are consistent with the page.
    long total = dataObjectDAO.countByCollectionByShepardIds(collectionOgmId, params);
    int firstIndex = page * pageSize;
    int lastIndex = result.isEmpty() ? -1 : firstIndex + result.size() - 1;
    String contentRange = "dataobjects " + firstIndex + "-" + lastIndex + "/" + total;

    // DB-OPT5: serialise via the per-request Jackson writer so ?fields= /
    // default-trim / ?include=full all flow through one code path.
    ObjectWriter writer = DataObjectListFieldFilter.writerFor(objectMapper, fields, includeFull);
    String body;
    try {
      body = writer.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      return problem(PROBLEM_TYPE_INTERNAL, "Internal server error",
        Response.Status.INTERNAL_SERVER_ERROR, "Failed to serialise DataObject list response");
    }

    return Response.ok(body, MediaType.APPLICATION_JSON)
      .header("Content-Range", contentRange)
      .header("X-Total-Count", total)
      .header("Cache-Control", "max-age=300, must-revalidate")
      .header("X-Shepard-Payload-Diet", fields != null && !fields.isBlank() ? "fields" : (includeFull ? "full" : "default-trim"))
      .build();
  }

  @GET
  @Path("/{dataObjectAppId}")
  @Produces({ MediaType.APPLICATION_JSON, "application/ld+json" })
  @Operation(
    operationId = "getDataObjectV2",
    summary = "Get a DataObject by appId.",
    description =
      "Returns the full `DataObjectIO` shape for the DataObject identified by " +
      "`dataObjectAppId` (UUID v7) within the Collection identified by `collectionAppId`.\n\n" +
      "Content negotiation (M4I-c): Pass `Accept: application/ld+json; profile=\"https://w3id.org/nfdi4ing/metadata4ing/\"` " +
      "(or short `profile=metadata4ing`) to receive a metadata4ing (NFDI4Ing m4i 1.4.0) " +
      "JSON-LD projection of the DataObject — m4i:InvestigatedObject type, dcterms:identifier/title, " +
      "schema:dateCreated, m4i:hasIdentifier (KIP1a PID), obo:RO_0002233/4 (predecessor/successor), " +
      "prov:wasGeneratedBy + m4i:realizesMethod (most-recent Activity), m4i:hasEmployedTool, " +
      "and m4i:hasNumericalVariable (numeric SemanticAnnotations with QUDT unit refs) + " +
      "schema:keywords (free-text annotations). Plain JSON callers see no change. " +
      "Unknown profile → 406 RFC 7807 `dataobject.unsupported-profile`. Shape contract: " +
      "`backend/src/main/resources/shapes/m4i-dataobject-shape.ttl`. Design: aidocs/semantics/94 §4.3.\n\n" +
      "The response includes `id` (legacy long), `appId` (UUID v7, canonical), `name`, " +
      "`description`, `status`, `attributes` (string-to-string map), and timestamps.\n\n" +
      "Auth: Read permission on the parent Collection (DataObjects inherit Collection " +
      "permissions; there is no per-DataObject permission node). Returns 404 when either " +
      "`collectionAppId` or `dataObjectAppId` is unknown, or when the DataObject does not " +
      "belong to the stated Collection.\n\n" +
      "Next step: `GET /v2/references?dataObjectAppId={dataObjectAppId}` to list all references, or " +
      "`PATCH /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` to update."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full DataObject detail for the requested DataObject, including typed container lists and relationship summaries.",
    content = @Content(schema = @Schema(implementation = DataObjectDetailV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId, or it doesn't belong to that Collection.")
  public Response get(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @HeaderParam(HttpHeaders.ACCEPT) String acceptHeader,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection or DataObject found for the given appIds");
    }

    // DataObjects don't have their own :Permissions node — access is
    // inherited from the parent Collection. The walk helper handles the
    // Cypher hop; gating on dataObjectOgmId directly always 403'd because
    // PermissionsDAO.findByEntityNeo4jId returns null for DOs.
    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    DataObject d = dataObjectService.getDataObject(collectionOgmId, dataObjectOgmId);

    // M4I-c — content-negotiation. Caller requested
    // `Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"`
    // (or the short `profile=metadata4ing`) → return the m4i flavoured
    // projection. Plain `application/json` (no profile) → today's
    // DataObjectDetailV2IO shape. Unknown profile → 406 RFC 7807.
    if (isJsonLdRequested(acceptHeader)) {
      Response profileError = enforceM4iProfile(acceptHeader);
      if (profileError != null) return profileError;
      ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);
      if (profile == ProvJsonLdRenderer.ProfileChoice.M4I) {
        Map<String, Object> body = m4iDataObjectRenderer.renderDataObject(d);
        return Response.ok(body)
          .type(M4iDataObjectRenderer.MEDIA_TYPE + ";profile=\"" + M4iDataObjectRenderer.M4I_PROFILE_URI + "\"")
          .header("Cache-Control", "max-age=300, must-revalidate")
          .build();
      }
      // PROV_O on a DataObject doesn't have a defined projection in
      // this slice; fall through to default JSON. (Reserved for a
      // future PROV-O DataObject view.)
    }

    DataObjectDetailV2IO io = new DataObjectDetailV2IO(d);
    DataObjectDetailV2IO.Containers containers = buildContainersFromCypher(dataObjectAppId);
    io.setContainers(containers);

    // API1 — populate per-kind reference appId arrays so MCP agents and REST
    // clients can navigate to containers without dereferencing opaque OGM long IDs.
    List<String> tsRefIds = containers.getTimeseries().stream()
      .map(de.dlr.shepard.v2.dataobject.io.ContainerRefIO::getReferenceAppId)
      .filter(s -> s != null)
      .collect(Collectors.toList());
    List<String> fileRefIds = containers.getFiles().stream()
      .map(de.dlr.shepard.v2.dataobject.io.ContainerRefIO::getReferenceAppId)
      .filter(s -> s != null)
      .collect(Collectors.toList());
    List<String> sdRefIds = containers.getStructuredData().stream()
      .map(de.dlr.shepard.v2.dataobject.io.ContainerRefIO::getReferenceAppId)
      .filter(s -> s != null)
      .collect(Collectors.toList());
    io.setTimeseriesReferenceAppIds(tsRefIds.isEmpty() ? null : tsRefIds);
    io.setFileReferenceAppIds(fileRefIds.isEmpty() ? null : fileRefIds);
    io.setStructuredDataReferenceAppIds(sdRefIds.isEmpty() ? null : sdRefIds);

    return Response.ok(io)
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }

  @POST
  @Operation(
    operationId = "createDataObjectV2",
    summary = "Create a DataObject inside a Collection.",
    description =
      "Creates a `:DataObject` in the Collection identified by `collectionAppId` " +
      "and returns the full entity (as `DataObjectDetail`) in the 201 body. " +
      "The server mints `appId` (UUID v7) and `id` (legacy long).\n\n" +
      "Body fields:\n" +
      "  - `name` (string, required, non-blank).\n" +
      "  - `description` (string, optional, CommonMark + GFM).\n" +
      "  - `attributes` (string-to-string map, optional).\n" +
      "  - `status` (optional, DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED/NCR_OPEN/ON_HOLD/REJECTED/CERTIFIED;\n" +
      "    NCR_OPEN/ON_HOLD/REJECTED/CERTIFIED require the 'quality-engineer' role — MFG1).\n" +
      "  - `provenanceMode` (optional, PROV1j / EU AI Act Art. 50): 'human', 'ai', " +
      "    or 'collaborative'. When omitted, auto-detected from the `X-AI-Agent` " +
      "    request header (present and non-blank → 'ai'; otherwise null).\n\n" +
      "Example body: `{\"name\": \"TR-001\", \"description\": \"hot-fire run\", " +
      "\"attributes\": {\"campaign\": \"Q3\"}}`.\n\n" +
      "Auth: Write on the parent Collection.\n\n" +
      "Side effects: ProvenanceCaptureFilter records a `CREATE` Activity " +
      "addressable at `GET /v2/provenance/entity/{appId}`. Versioning HEAD is " +
      "advanced on the parent Collection.\n\n" +
      "Next step: `POST /shepard/api/collections/{cid}/data-objects/{doid}/" +
      "timeseriesReferences` (or fileReferences / structuredDataReferences) to " +
      "attach payload, or `GET /v2/collections/{collectionAppId}/data-objects/" +
      "{dataObjectAppId}` to confirm the entity."
  )
  @APIResponse(
    responseCode = "201",
    description = "DataObject created. Response is a DataObjectDetail shape (superset of DataObjectIO).",
    content = @Content(schema = @Schema(implementation = DataObjectDetailV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — body validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response create(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CreateDataObjectV2IO.class))
    ) @Valid CreateDataObjectV2IO body,
    @HeaderParam("X-AI-Agent") String aiAgentHeader,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection found for collectionAppId");

    Response gate = enforceAccess(collectionOgmId, AccessType.Write, sc);
    if (gate != null) return gate;

    // PROV1j (EU AI Act Art. 50): auto-detect provenanceMode from X-AI-Agent header
    // when the caller did not supply it explicitly. Mirrors the SEMA-V6-007 pattern
    // used by SemanticAnnotationV2Rest.
    if (body.getProvenanceMode() == null && aiAgentHeader != null && !aiAgentHeader.isBlank()) {
      body.setProvenanceMode("ai");
    }

    DataObject created = dataObjectService.createDataObject(collectionOgmId, body);
    return Response.status(Response.Status.CREATED).entity(new DataObjectDetailV2IO(created)).build();
  }

  @PATCH
  @Path("/{dataObjectAppId}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchDataObjectV2",
    summary = "Partially update a DataObject (RFC 7396 JSON Merge Patch).",
    description =
      "Applies an RFC 7396 JSON Merge Patch to the `:DataObject` identified by " +
      "`dataObjectAppId` within `collectionAppId`:\n" +
      "  - Fields PRESENT in the body REPLACE the corresponding fields.\n" +
      "  - Fields ABSENT from the body are LEFT UNCHANGED.\n" +
      "  - Fields set to explicit JSON `null` are CLEARED (except `name`, which is " +
      "    `@NotBlank` — clearing it returns 400).\n\n" +
      "The merged result is Bean-Validated; violations on the final state return 400.\n\n" +
      "Example: rename without touching other fields — `{\"name\": \"TR-001-renamed\"}`.\n" +
      "Example: advance status — `{\"status\": \"IN_REVIEW\"}`.\n" +
      "Example: add an attribute — `{\"attributes\": {\"campaign\": \"Q3\"}}`.\n\n" +
      "Content-Type: prefer `application/merge-patch+json` (RFC 7396); " +
      "`application/json` is also accepted.\n\n" +
      "Auth: Write permission on the parent Collection. DataObjects do not have their " +
      "own permission node — the check walks up to the Collection.\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records an `UPDATE` Activity " +
      "addressable at `GET /v2/provenance/entity/{appId}`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full DataObjectIO reflecting the state after the patch was applied.",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or Bean Validation failed on the merged state (e.g. name is blank).")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId, or it doesn't belong to that Collection.")
  public Response patch(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @RequestBody(
      required = true,
      description = "Partial DataObject (RFC 7396). Every field is optional; absent fields are preserved.",
      content = @Content(
        mediaType = Constants.APPLICATION_MERGE_PATCH_JSON,
        schema = @Schema(implementation = DataObjectIO.class)
      )
    ) JsonNode patch,
    @Context SecurityContext sc
  ) {
    if (patch == null || !patch.isObject()) {
      throw new InvalidBodyException("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)");
    }

    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection or DataObject found for the given appIds");
    }

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    DataObject existing = dataObjectService.getDataObject(collectionOgmId, dataObjectOgmId);
    DataObjectIO merged = new DataObjectIO(existing);
    try {
      objectMapper.readerForUpdating(merged).readValue(patch);
    } catch (JsonProcessingException e) {
      throw new InvalidBodyException("Invalid JSON Merge Patch body: %s".formatted(e.getOriginalMessage()));
    } catch (IOException e) {
      throw new InvalidBodyException("Could not read JSON Merge Patch body: %s".formatted(e.getMessage()));
    }

    Set<ConstraintViolation<DataObjectIO>> violations = validator.validate(merged);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    DataObject updated = dataObjectService.updateDataObject(collectionOgmId, dataObjectOgmId, merged);
    return Response.ok(new DataObjectIO(updated)).build();
  }

  @DELETE
  @Path("/{dataObjectAppId}")
  @Operation(
    operationId = "deleteDataObjectV2",
    summary = "Delete a DataObject and its references.",
    description =
      "Soft-deletes the `:DataObject` identified by `dataObjectAppId` within the Collection " +
      "identified by `collectionAppId`. Cascades to the DataObject's attached references " +
      "(TimeseriesReferences, FileReferences, StructuredDataReferences, CollectionReferences, " +
      "LabJournalEntries). The underlying containers (FileContainer, TimeseriesContainer, " +
      "StructuredDataContainer) are not deleted — they are top-level entities independent " +
      "of the DataObject hierarchy; use the container-kind DELETE endpoints to remove them.\n\n" +
      "Idempotency: returns 204 whether or not the DataObject existed before the call.\n\n" +
      "Auth: Write permission on the parent Collection.\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records a `DELETE` Activity addressable at " +
      "`GET /v2/provenance/entity/{appId}`. Subscriptions attached to the DataObject fire."
  )
  @APIResponse(responseCode = "204", description = "DataObject and its references deleted (or DataObject was already gone).")
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId, or it doesn't belong to that Collection.")
  public Response delete(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection or DataObject found for the given appIds");
    }

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    dataObjectService.deleteDataObject(collectionOgmId, dataObjectOgmId);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  // ── ANC-1: ancestry / descendancy sub-endpoints ──────────────────────────

  @GET
  @Path("/{dataObjectAppId}/predecessors")
  @Operation(
    operationId = "predecessors",
    summary = "List direct predecessors of a DataObject.",
    description =
      "Returns the compact summary (appId, id, name, status) of each non-deleted DataObject " +
      "that is a direct predecessor of the DataObject identified by `dataObjectAppId`.\n\n" +
      "Results are wrapped in a `PagedResponseIO` envelope with `total`, `page`, and `pageSize`.\n\n" +
      "Auth: Read permission on the parent Collection (inherited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Direct predecessors (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response predecessors(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Maximum items per page, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (dataObjectOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject found for dataObjectAppId");

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection found for collectionAppId");

    int total = (int) Math.min(dataObjectService.countPredecessors(dataObjectAppId), (long) Integer.MAX_VALUE);
    int skip = (int) Math.min((long) page * pageSize, Integer.MAX_VALUE);
    List<DataObjectSummaryIO> items = dataObjectService.listPredecessors(dataObjectAppId, skip, pageSize)
        .stream().map(DataObjectSummaryIO::new).collect(java.util.stream.Collectors.toList());
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }

  // ── QM1b: set the relationship type of an existing predecessor edge ──────

  @PATCH
  @Path("/{dataObjectAppId}/predecessors/{predecessorAppId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
    operationId = "patchPredecessorEdge",
    summary = "QM1b — set the PROV-O / FAIR²R relationship type on an existing predecessor edge.",
    description =
      "Sets / replaces the relationship type for the predecessor edge from the DataObject " +
      "identified by `dataObjectAppId` to the predecessor identified by `predecessorAppId`. " +
      "The edge must already exist (use create / merge-patch to add new predecessor links).\n\n" +
      "Allowed `relationshipType` values:\n" +
      "  - `prov:wasInformedBy` — default / generic informational dependency (the QM1b 'normal' kind)\n" +
      "  - `prov:wasRevisionOf` — direct revision / correction (the QM1b 're-test' kind)\n" +
      "  - `fair2r:repairs` — rework / NCR-repair (the QM1b 'rework' kind)\n" +
      "  - `fair2r:concession` — concession / use-as-is disposition (QM1b)\n\n" +
      "Idempotent: re-running with the same `relationshipType` is a no-op.\n\n" +
      "Auth: Write permission on the parent Collection.\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records an `UPDATE` Activity addressable at " +
      "`GET /v2/provenance/entity/{appId}`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated DataObject detail; `typedPredecessorSummaries` reflects the new relationship type.",
    content = @Content(schema = @Schema(implementation = DataObjectDetailV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing or invalid `relationshipType` (not in the allowed set).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the parent Collection.")
  @APIResponse(responseCode = "404",
    description = "DataObject or predecessor not found, or no edge from this DataObject to that predecessor.")
  public Response patchPredecessorEdge(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @PathParam("predecessorAppId") @NotBlank String predecessorAppId,
    @RequestBody(
      required = true,
      description = "Single-field body: relationshipType. See operation description for allowed values.",
      content = @Content(schema = @Schema(implementation = PredecessorEdgePatchIO.class))
    ) PredecessorEdgePatchIO body,
    @Context SecurityContext sc
  ) {
    if (body == null) {
      throw new InvalidBodyException("PATCH body must include 'relationshipType'.");
    }
    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection or DataObject found for the given appIds");
    }

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    // Service throws InvalidBodyException (→ 400) on bad type,
    // InvalidPathException (→ 404) on missing DO or missing edge.
    DataObject updated = dataObjectService.setPredecessorRelationshipType(
      dataObjectOgmId, predecessorAppId, body.relationshipType()
    );
    return Response.ok(new DataObjectDetailV2IO(updated)).build();
  }

  @GET
  @Path("/{dataObjectAppId}/successors")
  @Operation(
    operationId = "successors",
    summary = "List direct successors of a DataObject.",
    description =
      "Returns the compact summary (appId, id, name, status) of each non-deleted DataObject " +
      "that is a direct successor of the DataObject identified by `dataObjectAppId`.\n\n" +
      "Results are wrapped in a `PagedResponseIO` envelope with `total`, `page`, and `pageSize`.\n\n" +
      "Auth: Read permission on the parent Collection (inherited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Direct successors (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response successors(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Maximum items per page, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (dataObjectOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject found for dataObjectAppId");

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection found for collectionAppId");

    int total = (int) Math.min(dataObjectService.countSuccessors(dataObjectAppId), (long) Integer.MAX_VALUE);
    int skip = (int) Math.min((long) page * pageSize, Integer.MAX_VALUE);
    List<DataObjectSummaryIO> items = dataObjectService.listSuccessors(dataObjectAppId, skip, pageSize)
        .stream().map(DataObjectSummaryIO::new).collect(java.util.stream.Collectors.toList());
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize)).build();
  }

  @GET
  @Path("/{dataObjectAppId}/children")
  @Operation(
    operationId = "children",
    summary = "List direct children of a DataObject.",
    description =
      "Returns the compact summary (appId, id, name, status) of each non-deleted DataObject " +
      "that is a direct child of the DataObject identified by `dataObjectAppId`.\n\n" +
      "Results are wrapped in a `PagedResponseIO` envelope with `total`, `page`, and `pageSize`.\n\n" +
      "Auth: Read permission on the parent Collection (inherited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Direct children (may be empty).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response children(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Maximum items per page, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext sc
  ) {
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (dataObjectOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject found for dataObjectAppId");

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No Collection found for collectionAppId");

    int total = (int) Math.min(dataObjectService.countChildren(dataObjectAppId), (long) Integer.MAX_VALUE);
    int skip = (int) Math.min((long) page * pageSize, Integer.MAX_VALUE);
    List<DataObjectSummaryIO> items = dataObjectService.listChildren(dataObjectAppId, skip, pageSize)
        .stream().map(DataObjectSummaryIO::new).collect(java.util.stream.Collectors.toList());
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize)).build();
  }

  @GET
  @Path("/{dataObjectAppId}/predecessor-chain")
  @Operation(
    operationId = "predecessorChain",
    summary = "Traverse the predecessor chain up to a given depth.",
    description =
      "Traverses the `has_successor` edges backwards from the DataObject identified by " +
      "`dataObjectAppId` up to `depth` hops (default 10, clamped at 50 server-side). " +
      "Returns compact summaries of all reachable non-deleted predecessors, excluding the " +
      "start node itself. Ordering is by shepardId (insert order approximation).\n\n" +
      "Results are wrapped in a `PagedResponseIO` envelope with `total`, `page=0`, and " +
      "`pageSize=total` (chains are bounded by the `depth` param, not paged).\n\n" +
      "Auth: Read permission on the parent Collection (inherited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Predecessor chain (may be empty when no predecessors exist).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response predecessorChain(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Parameter(description = "Maximum chain depth (default 10). Clamped server-side to [1, 50] — values below 1 become 1; values above 50 are silently clamped to 50.")
    @QueryParam("depth") @DefaultValue("10") @Max(50) @PositiveOrZero int depth,
    @Context SecurityContext sc
  ) {
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (dataObjectOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject found for dataObjectAppId");

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    List<DataObject> chain = dataObjectDAO.findPredecessorChain(dataObjectAppId, depth);
    List<DataObjectSummaryIO> result = new ArrayList<>(chain.size());
    for (DataObject d : chain) result.add(new DataObjectSummaryIO(d));
    return Response.ok(new PagedResponseIO<>(result, result.size(), 0, result.size())).build();
  }

  @GET
  @Path("/{dataObjectAppId}/successor-chain")
  @Operation(
    operationId = "successorChain",
    summary = "Traverse the successor chain up to a given depth.",
    description =
      "Traverses the `has_successor` edges forwards from the DataObject identified by " +
      "`dataObjectAppId` up to `depth` hops (default 10, clamped at 50 server-side). " +
      "Returns compact summaries of all reachable non-deleted successors, excluding the " +
      "start node itself. Ordering is by shepardId (insert order approximation).\n\n" +
      "Results are wrapped in a `PagedResponseIO` envelope with `total`, `page=0`, and " +
      "`pageSize=total` (chains are bounded by the `depth` param, not paged).\n\n" +
      "Auth: Read permission on the parent Collection (inherited)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Successor chain (may be empty when no successors exist).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response successorChain(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Parameter(description = "Maximum chain depth (default 10). Clamped server-side to [1, 50] — values below 1 become 1; values above 50 are silently clamped to 50.")
    @QueryParam("depth") @DefaultValue("10") @Max(50) @PositiveOrZero int depth,
    @Context SecurityContext sc
  ) {
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (dataObjectOgmId == null) return problem(PROBLEM_TYPE_NOT_FOUND, "Not found", Response.Status.NOT_FOUND, "No DataObject found for dataObjectAppId");

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    List<DataObject> chain = dataObjectDAO.findSuccessorChain(dataObjectAppId, depth);
    List<DataObjectSummaryIO> result = new ArrayList<>(chain.size());
    for (DataObject d : chain) result.add(new DataObjectSummaryIO(d));
    return Response.ok(new PagedResponseIO<>(result, result.size(), 0, result.size())).build();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  private Response enforceAccess(long ogmId, AccessType accessType, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Permission denied", Response.Status.FORBIDDEN, "Caller lacks the required permission on this resource");
    }
    return null;
  }

  /** Same shape as {@link #enforceAccess}, but walks DO → parent Collection
   *  via the permissions service helper because DOs have no own perms node. */
  private Response enforceDataObjectAccess(String dataObjectAppId, AccessType accessType, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, "No valid JWT or API key was provided");
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, accessType, caller)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Permission denied", Response.Status.FORBIDDEN, "Caller lacks the required permission on this DataObject");
    }
    return null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  /**
   * REF-1 fix — builds the {@link DataObjectDetailV2IO.Containers} from a
   * direct Cypher query rather than OGM entity traversal. OGM depth-1 loading
   * returns {@code BasicReference} instances, so {@code instanceof
   * TimeseriesReference} always fails; this bypass is needed until the OGM
   * load depth is raised globally.
   *
   * <p>NEO-AUDIT-008: {@code findContainersByDataObjectAppId} now returns a
   * single-row result with three list columns (one per ref kind), eliminating
   * the Cartesian product of the old sibling-{@code OPTIONAL MATCH} shape.
   * This method reads those lists directly.
   */
  @SuppressWarnings("unchecked")
  private DataObjectDetailV2IO.Containers buildContainersFromCypher(String dataObjectAppId) {
    var timeseries = new ArrayList<de.dlr.shepard.v2.dataobject.io.ContainerRefIO>();
    var files = new ArrayList<de.dlr.shepard.v2.dataobject.io.ContainerRefIO>();
    var structuredData = new ArrayList<de.dlr.shepard.v2.dataobject.io.ContainerRefIO>();

    Map<String, Object> row = dataObjectDAO.findContainersByDataObjectAppId(dataObjectAppId);

    Object tsRefsRaw = row.get("tsRefs");
    if (tsRefsRaw instanceof Iterable<?> tsRefList) {
      for (Object entry : tsRefList) {
        if (entry instanceof Map<?, ?> m) {
          var ref = (Map<String, Object>) m;
          Object refShepardId = ref.get("refShepardId");
          if (refShepardId instanceof Number) {
            timeseries.add(new de.dlr.shepard.v2.dataobject.io.ContainerRefIO(
              (String) ref.get("containerAppId"),
              (String) ref.get("containerName"),
              (String) ref.get("refAppId")
            ));
          }
        }
      }
    }

    Object fileRefsRaw = row.get("fileRefs");
    if (fileRefsRaw instanceof Iterable<?> fileRefList) {
      for (Object entry : fileRefList) {
        if (entry instanceof Map<?, ?> m) {
          var ref = (Map<String, Object>) m;
          Object refShepardId = ref.get("refShepardId");
          if (refShepardId instanceof Number) {
            files.add(new de.dlr.shepard.v2.dataobject.io.ContainerRefIO(
              (String) ref.get("containerAppId"),
              (String) ref.get("containerName"),
              (String) ref.get("refAppId")
            ));
          }
        }
      }
    }

    Object sdRefsRaw = row.get("sdRefs");
    if (sdRefsRaw instanceof Iterable<?> sdRefList) {
      for (Object entry : sdRefList) {
        if (entry instanceof Map<?, ?> m) {
          var ref = (Map<String, Object>) m;
          Object refShepardId = ref.get("refShepardId");
          if (refShepardId instanceof Number) {
            structuredData.add(new de.dlr.shepard.v2.dataobject.io.ContainerRefIO(
              (String) ref.get("containerAppId"),
              (String) ref.get("containerName"),
              (String) ref.get("refAppId")
            ));
          }
        }
      }
    }

    return new DataObjectDetailV2IO.Containers(timeseries, files, structuredData);
  }

  // ── M4I-c content-negotiation helpers ─────────────────────────────────

  /**
   * Returns {@code true} iff the {@code Accept} header carries an
   * {@code application/ld+json} clause. Used to decide whether to enter
   * the M4I-c content-neg branch; absent or other media types fall
   * through to the default JSON projection.
   */
  private static boolean isJsonLdRequested(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isBlank()) return false;
    String[] clauses = acceptHeader.split(",");
    for (String clause : clauses) {
      String mediaType = clause.split(";", 2)[0].trim();
      if (mediaType.equalsIgnoreCase("application/ld+json")) return true;
    }
    return false;
  }

  /**
   * If the caller's {@code Accept} header carries a
   * {@code profile=} parameter that resolves neither to PROV-O nor to
   * the m4i profile, surface a 406 RFC 7807. {@code null} return means
   * "no objection — proceed."
   *
   * <p>Mirrors {@code ProvenanceRest.enforceJsonLdProfile} but with a
   * DataObject-scoped problem type.
   */
  private static Response enforceM4iProfile(String acceptHeader) {
    String raw = ProvJsonLdRenderer.extractProfileParam(acceptHeader);
    if (raw == null || raw.isBlank()) return null;
    ProvJsonLdRenderer.ProfileChoice profile = ProvJsonLdRenderer.resolveProfile(acceptHeader);
    if (profile != null) return null;
    de.dlr.shepard.common.exceptions.ProblemJson body = new de.dlr.shepard.common.exceptions.ProblemJson(
      "https://noheton.github.io/shepard/errors/dataobject.unsupported-profile",
      "Unsupported JSON-LD profile",
      Response.Status.NOT_ACCEPTABLE.getStatusCode(),
      "The profile= parameter '" + raw + "' on the Accept header is not recognised. " +
      "Supported: '" + M4iDataObjectRenderer.M4I_PROFILE_URI + "' (or short 'metadata4ing'). " +
      "Omit the parameter for the canonical JSON projection.",
      null
    );
    return Response.status(Response.Status.NOT_ACCEPTABLE)
      .entity(body)
      .type("application/problem+json")
      .build();
  }
}
