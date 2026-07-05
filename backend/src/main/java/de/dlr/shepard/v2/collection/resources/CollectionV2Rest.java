package de.dlr.shepard.v2.collection.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.collection.io.CollectionV2IO;
import de.dlr.shepard.v2.collection.io.CreateCollectionV2IO;
import de.dlr.shepard.v2.collection.io.UpdateCollectionV2IO;
import io.quarkus.security.Authenticated;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import java.io.IOException;
import java.util.Set;
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
 * L2d Phase A — {@code /v2/collections}: the canonical Collection CRUD on
 * this fork's development shelf, keyed by {@code appId} (UUID v7) instead
 * of the upstream Neo4j-OGM Long primary key.
 *
 * <p>This is the first {@code /v2/} resource to expose a core entity by
 * its application identifier. The upstream {@link
 * de.dlr.shepard.context.collection.endpoints.CollectionRest} at
 * {@code /shepard/api/collections} stays byte-frozen for upstream
 * clients; both surfaces hit the same {@link CollectionService}, with
 * this resource translating {@code appId → ogmId} at the boundary via
 * {@link EntityIdResolver}.
 *
 * <p><b>Scope (Phase A).</b> Core CRUD only — {@code GET} list,
 * {@code GET} one, {@code POST} create, {@code PATCH} merge-patch
 * (RFC 7396), {@code DELETE}. Deferred to Phase B:
 * <ul>
 *   <li>Permissions / roles endpoints — move to a dedicated
 *       {@code /v2/permissions/{collectionAppId}} resource alongside
 *       the cross-entity permissions migration.</li>
 *   <li>Export — {@code /v2/collections/{appId}/export-url} already
 *       exists ({@link de.dlr.shepard.v2.collection.resources.CollectionExportUrlRest}).</li>
 *   <li>{@code PUT} full-replace — {@code PATCH} is the {@code /v2/}
 *       preferred shape per the L2d spec; PUT can land later if a
 *       concrete use case demands it.</li>
 *   <li>{@code versionUID} query parameter — versioned reads land
 *       alongside the v2 snapshot work.</li>
 * </ul>
 *
 * <p><b>Auth.</b> Per-resource, matching the established {@code /v2/}
 * pattern (see {@code CollectionSnapshotRest}, {@code
 * CollectionPropertiesRest}, {@code CollectionWatchesRest}):
 * {@link EntityIdResolver#resolveLong} translates the {@code appId} to
 * the OGM id, then {@link PermissionsService#isAccessTypeAllowedForUser}
 * gates the call. {@code 401} when unauthenticated; {@code 404} when
 * the {@code appId} doesn't resolve; {@code 403} when the caller lacks
 * the right {@link AccessType}.
 *
 * <p><b>DTO.</b> Reuses {@link CollectionIO}. The IO already carries
 * both {@code id: long} (legacy) and {@code appId: string} (canonical)
 * fields on {@link de.dlr.shepard.common.neo4j.io.BasicEntityIO}, so a
 * v2 client never needs to read the long. Phase B will introduce a
 * v2-flavored variant that exposes {@code dataObjectAppIds} /
 * {@code incomingAppIds} as {@code String[]} instead of {@code long[]} —
 * out of scope here.
 *
 * <p>Cross-references: {@code aidocs/25 §4} (L2d phase 4 spec);
 * {@code aidocs/68} (V2BASE prerequisite framing); CLAUDE.md "API-version
 * policy" — every new endpoint lands under {@code /v2/}.
 */
@Path("/v2/collections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collections")
public class CollectionV2Rest {

  private static final String PT_NOT_FOUND    = "/problems/collections.not-found";
  private static final String PT_UNAUTHORIZED = "/problems/collections.unauthorized";
  private static final String PT_FORBIDDEN    = "/problems/collections.forbidden";

  @Inject
  CollectionService collectionService;

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
    operationId = "listCollections",
    summary = "List Collections the caller may read.",
    description =
      "Returns a page of `:Collection` entities (`CollectionIO` JSON shape) that the " +
      "authenticated caller has Read permission on. The page is unordered by default; " +
      "supply `orderBy` (one of the `DataObjectAttributes` enum: `createdAt`, `name`, " +
      "`status`, …) with `orderDesc=true` for a sorted result.\n\n" +
      "Pagination: omit `page` / `pageSize` to get the first 50; supply both to paginate. " +
      "The server caps `pageSize` at 200 to avoid unbounded result sets.\n\n" +
      "Filtering: `name` does a case-insensitive substring match.\n\n" +
      "Each returned `CollectionV2IO` carries `appId` (canonical UUID v7) as the " +
      "stable identifier; use the matching `/v2/...` endpoints for follow-up calls.\n\n" +
      "Auth: no role gate — the result is filtered server-side to entities the caller " +
      "may Read, so an unauthorised caller simply gets a smaller list (not 403).\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}` to fetch a specific " +
      "Collection with its DataObjects expanded."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Header X-Total-Count = total count before paging (kept during deprecation window, APISIMP-PAGINATION-ENVELOPE).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "400",
    description = "Bean Validation rejected the query — `page` is negative, or `pageSize` is outside [1, 200].")
  @APIResponse(responseCode = "401",
    description = "Authentication required (no JWT and no X-API-KEY).")
  public Response list(
    @Parameter(description = "Optional case-insensitive substring filter on Collection name. Omit to return all collections the caller can read.")
    @QueryParam(Constants.QP_NAME) String name,
    @Parameter(description = "0-based page index. Default 0. Negative values are rejected (400).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size. Default 50. Must be between 1 and 200 inclusive (400 otherwise).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {

    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);

    long total = collectionService.countAllCollections(params);
    var items = collectionService.getAllCollections(params.withPageAndSize(page, pageSize)).stream()
        .map(CollectionV2IO::new)
        .toList();

    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
      .header("X-Total-Count", total)  // kept during deprecation window (APISIMP-PAGINATION-ENVELOPE)
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }

  @GET
  @Path("/{collectionAppId}")
  @Operation(
    operationId = "getCollectionV2",
    summary = "Get a Collection by appId, with DataObjects and incoming references.",
    description =
      "Returns the `:Collection` identified by `collectionAppId` (UUID v7) " +
      "in the full `CollectionIO` shape: name, description, status, attributes, " +
      "`dataObjectIds[]` (legacy long ids of contained DataObjects — use the " +
      "matching `/v2/collections/{collectionAppId}/data-objects` endpoint when " +
      "you want appIds), `incomingIds[]` (CollectionReferences pointing at this " +
      "Collection), `defaultFileContainerId` (nullable).\n\n" +
      "Auth: Read on the Collection. `404` is returned for unknown appIds " +
      "*before* the auth check — distinguishing 'doesn't exist' from " +
      "'exists but you can't see it' is intentionally not done (timing-safe).\n\n" +
      "Next step: `GET /v2/collections/{collectionAppId}/data-objects` for " +
      "the DataObject list, or `PATCH /v2/collections/{collectionAppId}` for " +
      "an RFC 7396 merge-patch update.",
    extensions = @Extension(name = "x-agent-hint", value = "Top-level container. appId is the stable identifier for all v2 calls.")
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection found.",
    content = @Content(schema = @Schema(implementation = CollectionV2IO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response get(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @Context SecurityContext sc
  ) {
    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "No Collection with appId " + collectionAppId);

    Response gate = enforceAccess(ogmId, AccessType.Read, sc);
    if (gate != null) return gate;

    Collection c = collectionService.getCollectionWithDataObjectsAndIncomingReferences(ogmId);
    return Response.ok(new CollectionV2IO(c))
      .header("Cache-Control", "max-age=300, must-revalidate")
      .build();
  }

  @POST
  @Operation(
    operationId = "createCollectionV2",
    summary = "Create a new Collection.",
    description =
      "Creates a `:Collection` and returns the full entity in the 201 body. " +
      "The server mints both `appId` (UUID v7, canonical) and `id` (legacy long); " +
      "the caller becomes the owner with Manage permission.\n\n" +
      "Body fields:\n" +
      "  - `name` (string, required, non-blank).\n" +
      "  - `description` (string, optional, supports CommonMark + GFM).\n" +
      "  - `attributes` (string-to-string map, optional; keys must not contain " +
      "the delimiter characters `Space, Comma, Point, Slash`).\n" +
      "  - `status` (optional, one of `DRAFT`, `IN_REVIEW`, `READY`, " +
      "`PUBLISHED`, `ARCHIVED`).\n" +
      "  - `defaultFileContainerAppId` (optional, UUID v7 appId of a " +
      "FileContainer to serve as the Collection's default; resolved server-side).\n\n" +
      "Example minimal body: `{\"name\": \"My experiment\"}`.\n" +
      "Example with attributes: `{\"name\": \"TR-001\", \"description\": \"Hot-fire run\", " +
      "\"attributes\": {\"campaign\": \"Q3\", \"site\": \"Lampoldshausen\"}, " +
      "\"status\": \"DRAFT\"}`.\n\n" +
      "Auth: any authenticated user can create a Collection (the request is " +
      "authenticated via JWT or `X-API-KEY`). The created Collection's " +
      "permission-type defaults to `PRIVATE`; flip it to `PUBLIC` via " +
      "`PUT /shepard/api/collections/{id}/permissions` to make it visible " +
      "to all other users.\n\n" +
      "Side effects: `ProvenanceCaptureFilter` records a `CREATE` Activity " +
      "addressable at `GET /v2/provenance/entity/{appId}`. A `:Version` node " +
      "is created with `Constants.HEAD` as its initial version.\n\n" +
      "Next step: `POST /v2/collections/{collectionAppId}/data-objects` to " +
      "add DataObjects, or `PATCH /v2/collections/{collectionAppId}` to set " +
      "additional metadata."
  )
  @APIResponse(
    responseCode = "201",
    description = "Collection created.",
    content = @Content(schema = @Schema(implementation = CollectionV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — body validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response create(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CreateCollectionV2IO.class))
    ) @Valid CreateCollectionV2IO body
  ) {
    CollectionIO io = new CollectionIO();
    io.setName(body.getName());
    io.setDescription(body.getDescription());
    io.setAttributes(body.getAttributes() != null ? body.getAttributes() : new java.util.HashMap<>());
    io.setStatus(body.getStatus());
    io.setLicense(body.getLicense());
    io.setAccessRights(body.getAccessRights());
    io.setEmbargoEndDate(body.getEmbargoEndDate());
    io.setHeroImageUrl(body.getHeroImageUrl());
    io.setImportedFrom(body.getImportedFrom());
    io.setPromptLogMode(body.getPromptLogMode());
    io.setDefaultFileContainerAppId(body.getDefaultFileContainerAppId());
    Collection created = collectionService.createCollection(io);
    return Response.status(Response.Status.CREATED).entity(new CollectionV2IO(created)).build();
  }

  @PATCH
  @Path("/{collectionAppId}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    operationId = "patchCollectionV2",
    summary = "Partially update a Collection (RFC 7396 JSON Merge Patch).",
    description =
      "Applies an RFC 7396 JSON Merge Patch to the `:Collection`:\n" +
      "  - Fields PRESENT in the body REPLACE the corresponding fields.\n" +
      "  - Fields ABSENT from the body are LEFT UNCHANGED.\n" +
      "  - Fields set to explicit JSON `null` are CLEARED.\n\n" +
      "The merged result is Bean-Validated against `CollectionIO` constraints; " +
      "violations on the final state return 400 (e.g. clearing `name` would " +
      "produce a constraint violation because `name` is `@NotBlank`).\n\n" +
      "Example: rename a Collection without touching other fields — \n" +
      "`{\"name\": \"renamed\"}`. Clear the description — `{\"description\": null}`.\n\n" +
      "Auth: Write permission on the Collection.\n\n" +
      "Content-Type: prefer `application/merge-patch+json` (the RFC 7396 type); " +
      "the endpoint also accepts `application/json` for clients that can't set " +
      "the dedicated type.\n\n" +
      "Side effects: ProvenanceCaptureFilter records an `UPDATE` Activity. " +
      "The `:Version` HEAD is advanced; the previous state is reachable via " +
      "`GET /shepard/api/collections/{id}/versions`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection updated.",
    content = @Content(schema = @Schema(implementation = CollectionV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — body is not a JSON object or validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response patch(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @RequestBody(
      required = true,
      description = "Partial Collection (RFC 7396). Every field is optional; absent fields are preserved.",
      content = @Content(
        mediaType = Constants.APPLICATION_MERGE_PATCH_JSON,
        schema = @Schema(implementation = UpdateCollectionV2IO.class)
      )
    ) JsonNode patch,
    @Context SecurityContext sc
  ) {
    if (patch == null || !patch.isObject()) {
      throw new InvalidBodyException("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)");
    }

    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "No Collection with appId " + collectionAppId);

    Response gate = enforceAccess(ogmId, AccessType.Write, sc);
    if (gate != null) return gate;

    Collection existing = collectionService.getCollectionWithDataObjectsAndIncomingReferences(ogmId);
    CollectionIO merged = new CollectionIO(existing);
    try {
      objectMapper.readerForUpdating(merged).readValue(patch);
    } catch (JsonProcessingException e) {
      throw new InvalidBodyException("Invalid JSON Merge Patch body: %s".formatted(e.getOriginalMessage()));
    } catch (IOException e) {
      throw new InvalidBodyException("Could not read JSON Merge Patch body: %s".formatted(e.getMessage()));
    }

    Set<ConstraintViolation<CollectionIO>> violations = validator.validate(merged);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    Collection updated = collectionService.updateCollectionByShepardId(ogmId, merged);
    return Response.ok(new CollectionV2IO(updated)).build();
  }

  @DELETE
  @Path("/{collectionAppId}")
  @Operation(
    operationId = "deleteCollectionV2",
    summary = "Soft-delete a Collection and cascade.",
    description =
      "Marks the `:Collection` deleted (`deleted=true`) plus every reachable " +
      "DataObject, Reference, LabJournalEntry and Snapshot. The underlying " +
      "containers (FileContainer / TimeseriesContainer / StructuredDataContainer) " +
      "stay — they are top-level entities, not nested under a Collection, so " +
      "deleting a Collection orphans its references but does not destroy the " +
      "payload bytes. Use the container-kind DELETE endpoints for that.\n\n" +
      "Idempotency: deleting a non-existent or already-deleted Collection " +
      "returns 204 without error.\n\n" +
      "Auth: Write permission on the Collection.\n\n" +
      "Side effects: ProvenanceCaptureFilter records a `DELETE` Activity. " +
      "Subscriptions attached to the Collection fire on this write."
  )
  @APIResponse(responseCode = "204", description = "Collection deleted (or already gone).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response delete(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @Context SecurityContext sc
  ) {
    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return problem(PT_NOT_FOUND, "Collection not found", Response.Status.NOT_FOUND,
        "No Collection with appId " + collectionAppId);

    Response gate = enforceAccess(ogmId, AccessType.Write, sc);
    if (gate != null) return gate;

    collectionService.deleteCollection(ogmId);
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
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required", Response.Status.UNAUTHORIZED, null);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L)) {
      return problem(PT_FORBIDDEN, "Insufficient permission", Response.Status.FORBIDDEN,
          "Caller lacks the required permission on this Collection");
    }
    return null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
        .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
