package de.dlr.shepard.v2.collection.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
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
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
@Tag(name = "Collections (v2)")
public class CollectionV2Rest {

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
    summary = "List Collections.",
    description =
      "Returns a page of Collections the caller may Read. " +
      "Filtering: 'name' query param matches by name substring. " +
      "Pagination: page=0, size=50 by default (size capped at 200 server-side). " +
      "Sort: ordered by Neo4j-OGM id by default; 'orderBy' / 'orderDesc' query " +
      "params accept the same {@code DataObjectAttributes} enum the v1 endpoint takes."
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection page (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — page/size out of range.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("size") @DefaultValue("50") @PositiveOrZero int size
  ) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);

    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    params = params.withPageAndSize(safePage, safeSize);

    var collections = collectionService.getAllCollections(params);
    var result = new ArrayList<CollectionIO>(collections.size());
    for (var c : collections) {
      result.add(new CollectionIO(c));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{collectionAppId}")
  @Operation(
    summary = "Get a Collection by appId.",
    description =
      "Returns the Collection identified by 'collectionAppId', including " +
      "its DataObjects and incoming references (the same 'full' shape the " +
      "v1 endpoint serves). Requires Read permission."
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection found.",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response get(
    @PathParam("collectionAppId") @NotBlank String collectionAppId,
    @Context SecurityContext sc
  ) {
    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return Response.status(Response.Status.NOT_FOUND).build();

    Response gate = enforceAccess(ogmId, AccessType.Read, sc);
    if (gate != null) return gate;

    Collection c = collectionService.getCollectionWithDataObjectsAndIncomingReferences(ogmId);
    return Response.ok(new CollectionIO(c)).build();
  }

  @POST
  @Operation(
    summary = "Create a Collection.",
    description =
      "Creates a new Collection. Body fields: 'name' (required), 'description', " +
      "'attributes', 'status', 'defaultFileContainerId' (optional). The server " +
      "mints the 'appId' and returns it in the 201 response body."
  )
  @APIResponse(
    responseCode = "201",
    description = "Collection created.",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — body validation failed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response create(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionIO.class))
    ) @Valid CollectionIO body
  ) {
    Collection created = collectionService.createCollection(body);
    return Response.status(Response.Status.CREATED).entity(new CollectionIO(created)).build();
  }

  @PATCH
  @Path("/{collectionAppId}")
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Partially update a Collection (RFC 7396 JSON Merge Patch).",
    description =
      "Applies an RFC 7396 JSON Merge Patch to the Collection. Fields present " +
      "in the body replace the corresponding fields on the entity; absent fields " +
      "are left unchanged; explicit JSON null clears the field. The merged " +
      "result is Bean-Validated; constraint violations on the final state " +
      "return 400. Requires Write permission."
  )
  @APIResponse(
    responseCode = "200",
    description = "Collection updated.",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
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
        schema = @Schema(implementation = CollectionIO.class)
      )
    ) JsonNode patch,
    @Context SecurityContext sc
  ) {
    if (patch == null || !patch.isObject()) {
      throw new InvalidBodyException("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)");
    }

    Long ogmId = resolveOrNull(collectionAppId);
    if (ogmId == null) return Response.status(Response.Status.NOT_FOUND).build();

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
    return Response.ok(new CollectionIO(updated)).build();
  }

  @DELETE
  @Path("/{collectionAppId}")
  @Operation(
    summary = "Delete a Collection.",
    description =
      "Soft-deletes the Collection identified by 'collectionAppId' and " +
      "every entity reachable from it (DataObjects, References, …). " +
      "Idempotent: deleting a non-existent or already-deleted Collection " +
      "returns 204. Requires Write permission."
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
    if (ogmId == null) return Response.status(Response.Status.NOT_FOUND).build();

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
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId, accessType, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }
}
