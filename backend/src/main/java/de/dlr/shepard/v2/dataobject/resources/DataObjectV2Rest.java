package de.dlr.shepard.v2.dataobject.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
 * <p><b>Scope (Phase A.2).</b> Core CRUD only: list / get / create /
 * RFC-7396 merge-patch / delete. Deferred to Phase B:
 * <ul>
 *   <li>{@code parentId} / {@code predecessorId} / {@code successorId}
 *       filters — they exist on v1 as long-id query params; the v2
 *       shape will take appIds and translate, but the structural
 *       traversal endpoints (e.g. children, predecessors) are the
 *       natural home for those lookups and ship together.</li>
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
@Tag(name = "DataObjects (v2)")
public class DataObjectV2Rest {

  @Inject
  DataObjectService dataObjectService;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @Inject
  Validator validator;

  @Inject
  ObjectMapper objectMapper;

  @GET
  @Operation(
    summary = "List DataObjects under a Collection.",
    description =
      "Returns a page of `:DataObject` entities belonging to the Collection " +
      "identified by `collectionAppId`. Each row in the response carries the full " +
      "`DataObjectIO` fields plus three per-kind reference counts: " +
      "`timeseriesCount`, `fileCount`, `structuredDataCount`. Counts reflect " +
      "non-deleted references only and are computed in a single Cypher round-trip " +
      "(no N+1 queries).\n\n" +
      "Pagination: omit `page` / `size` to get the first 50; supply both to " +
      "paginate. `size` capped at 200 server-side.\n\n" +
      "Filtering: `name` does a case-insensitive substring match. Each row " +
      "also carries `referenceIds[]` (legacy long ids of all refs) and " +
      "`childrenIds[]` (direct child DOs).\n\n" +
      "Auth: Read on the parent Collection. DataObjects inherit Collection " +
      "permissions; there is no per-DO permission gate.\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` " +
      "to fetch a specific DataObject with full reference detail."
  )
  @APIResponse(
    responseCode = "200",
    description = "DataObject page (may be empty). Each item includes `timeseriesCount`, `fileCount`, `structuredDataCount`.",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectListItemV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("size") @DefaultValue("50") @PositiveOrZero int size,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = enforceAccess(collectionOgmId, AccessType.Read, sc);
    if (gate != null) return gate;

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);

    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    params = params.withPageAndSize(safePage, safeSize);

    var dataObjects = dataObjectService.getAllDataObjectsByShepardIds(collectionOgmId, params, null);

    // Collect appIds for the batch count query (one round-trip for the whole page).
    List<String> appIds = new ArrayList<>(dataObjects.size());
    for (var d : dataObjects) {
      if (d.getAppId() != null) appIds.add(d.getAppId());
    }
    Map<String, long[]> counts = dataObjectDAO.findRefCountsByAppIds(appIds);

    var result = new ArrayList<DataObjectListItemV2IO>(dataObjects.size());
    for (var d : dataObjects) {
      long[] c = d.getAppId() != null ? counts.getOrDefault(d.getAppId(), new long[] { 0, 0, 0 }) : new long[] { 0, 0, 0 };
      result.add(new DataObjectListItemV2IO(d, c[0], c[1], c[2]));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{dataObjectAppId}")
  @Operation(
    summary = "Get a DataObject by appId.",
    description =
      "Returns the full `DataObjectIO` shape for the DataObject identified by " +
      "`dataObjectAppId` (UUID v7) within the Collection identified by `collectionAppId`.\n\n" +
      "The response includes `id` (legacy long), `appId` (UUID v7, canonical), `name`, " +
      "`description`, `status`, `attributes` (string-to-string map), `referenceIds[]` " +
      "(legacy long ids of all references — timeseries, file, structured-data — attached " +
      "to this DataObject), `childrenIds[]` (direct child DataObjects), and timestamps.\n\n" +
      "Auth: Read permission on the parent Collection (DataObjects inherit Collection " +
      "permissions; there is no per-DataObject permission node). Returns 404 when either " +
      "`collectionAppId` or `dataObjectAppId` is unknown, or when the DataObject does not " +
      "belong to the stated Collection.\n\n" +
      "Next step: use the `referenceIds` values with the upstream " +
      "`GET /shepard/api/...` reference endpoints to fetch payload, or " +
      "`PATCH /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` to update."
  )
  @APIResponse(
    responseCode = "200",
    description = "Full DataObjectIO for the requested DataObject.",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId, or it doesn't belong to that Collection.")
  public Response get(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    Long dataObjectOgmId = resolveOrNull(dataObjectAppId);
    if (collectionOgmId == null || dataObjectOgmId == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // DataObjects don't have their own :Permissions node — access is
    // inherited from the parent Collection. The walk helper handles the
    // Cypher hop; gating on dataObjectOgmId directly always 403'd because
    // PermissionsDAO.findByEntityNeo4jId returns null for DOs.
    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Read, sc);
    if (gate != null) return gate;

    DataObject d = dataObjectService.getDataObject(collectionOgmId, dataObjectOgmId);
    return Response.ok(new DataObjectIO(d)).build();
  }

  @POST
  @Operation(
    summary = "Create a DataObject inside a Collection.",
    description =
      "Creates a `:DataObject` in the Collection identified by `collectionAppId` " +
      "and returns the full entity in the 201 body. The server mints `appId` " +
      "(UUID v7) and `id` (legacy long).\n\n" +
      "Body fields:\n" +
      "  - `name` (string, required, non-blank).\n" +
      "  - `description` (string, optional, CommonMark + GFM).\n" +
      "  - `attributes` (string-to-string map, optional).\n" +
      "  - `status` (optional, DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED).\n\n" +
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
    description = "DataObject created.",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — body validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response create(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO body,
    @Context SecurityContext sc
  ) {
    Long collectionOgmId = resolveOrNull(collectionAppId);
    if (collectionOgmId == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = enforceAccess(collectionOgmId, AccessType.Write, sc);
    if (gate != null) return gate;

    DataObject created = dataObjectService.createDataObject(collectionOgmId, body);
    return Response.status(Response.Status.CREATED).entity(new DataObjectIO(created)).build();
  }

  @PATCH
  @Path("/{dataObjectAppId}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
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
      return Response.status(Response.Status.NOT_FOUND).build();
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
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    Response gate = enforceDataObjectAccess(dataObjectAppId, AccessType.Write, sc);
    if (gate != null) return gate;

    dataObjectService.deleteDataObject(collectionOgmId, dataObjectOgmId);
    return Response.status(Response.Status.NO_CONTENT).build();
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
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  /** Same shape as {@link #enforceAccess}, but walks DO → parent Collection
   *  via the permissions service helper because DOs have no own perms node. */
  private Response enforceDataObjectAccess(String dataObjectAppId, AccessType accessType, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(dataObjectAppId, accessType, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
