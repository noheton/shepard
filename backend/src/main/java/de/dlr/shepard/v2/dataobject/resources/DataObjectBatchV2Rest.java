package de.dlr.shepard.v2.dataobject.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.ShepardException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchCreateItemIO;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchResponseIO;
import de.dlr.shepard.v2.dataobject.io.DataObjectBatchResultItemIO;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * MFFD-BATCH-01 — {@code POST /v2/data-objects/batch}.
 *
 * <p>Accepts a JSON array of up to 500 {@link DataObjectBatchCreateItemIO}
 * items and returns HTTP 207 Multi-Status with per-item outcomes. Each item
 * is processed independently: failures on item N do not prevent items
 * N+1..M from being attempted.
 *
 * <p><b>Cross-collection root.</b> This endpoint lives at {@code /v2/data-objects/batch}
 * rather than under a Collection path because a single batch request may target
 * <em>multiple</em> Collections; no single {@code collectionAppId} segment would
 * correctly scope it. Each item carries its own {@code collectionAppId} and is
 * permission-checked independently (Write on that Collection).
 *
 * <p><b>Permissions.</b> The caller must be authenticated. Each item's
 * target collection is permission-checked independently for Write access.
 * The permission result per {@code collectionAppId} is memoised within the
 * batch so repeated items targeting the same collection do not re-hit
 * the permissions service.
 *
 * <p><b>Provenance.</b> {@link
 * de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter} fires once for
 * the whole 207 response (HTTP 207 is in the 2xx range). One {@code :Activity}
 * node with verb {@code CREATE} is recorded for the batch request. Per-item
 * activity granularity is deferred to a future PROV2 slice.
 *
 * <p><b>Neo4j sessions.</b> Items are processed sequentially in a for-loop.
 * No {@code CompletableFuture} or parallel streams are used — each
 * {@code createDataObject} call executes in the same OGM session opened by
 * the request-scoped CDI context.
 */
@Path("/v2/data-objects/batch")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "DataObjects")
public class DataObjectBatchV2Rest {

  /** Maximum number of items accepted in a single batch request. */
  static final int MAX_BATCH_SIZE = 500;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  EntityIdResolver entityIdResolver;

  @POST
  @Operation(
    operationId = "batch",
    summary = "Bulk-create DataObjects across one or more Collections (HTTP 207).",
    description =
      "Accepts a JSON array of DataObject creation requests (1–500 items) and returns " +
      "HTTP 207 Multi-Status with a per-item outcome. Items are processed sequentially; " +
      "a failure on item N does not prevent items N+1..M from being attempted.\n\n" +
      "**Required fields per item:** `collectionAppId` (target Collection, UUID v7), " +
      "`name` (non-blank string).\n\n" +
      "**Optional fields per item:** `description`, `parentAppId` (UUID v7 of an existing " +
      "DataObject to use as the hierarchical parent), `attributes` (string→string map), " +
      "`status` (free-form string; suggested: DRAFT / IN_REVIEW / READY / PUBLISHED / ARCHIVED / NCR_OPEN / ON_HOLD / REJECTED / CERTIFIED;\n" +
      "    NCR_OPEN / ON_HOLD / REJECTED / CERTIFIED require the 'quality-engineer' role — MFG1).\n\n" +
      "**HTTP 400** is returned before any processing if the array is empty or exceeds 500 items.\n\n" +
      "**HTTP 207** is always returned for valid-sized batches, even when all items fail " +
      "(the `failed` counter reflects item-level failures; a genuine server error returns 500).\n\n" +
      "**Per-item error codes:** `COLLECTION_NOT_FOUND`, `FORBIDDEN`, " +
      "`PARENT_NOT_FOUND`, `INVALID_INPUT`, `INTERNAL_ERROR`.\n\n" +
      "**Auth:** Caller must be authenticated. Each item's `collectionAppId` requires Write " +
      "permission. The permission result per Collection is memoised within the batch — " +
      "repeated items targeting the same Collection do not re-query the permissions service.\n\n" +
      "**Provenance:** One `CREATE` `[:Activity]` is recorded for the whole batch request " +
      "(HTTP 207 is in the 2xx range, so `ProvenanceCaptureFilter` fires once). Per-item " +
      "provenance granularity is a PROV2 deferred item.\n\n" +
      "Example body: `[{\"collectionAppId\":\"018f…\",\"name\":\"TR-001\"}, " +
      "{\"collectionAppId\":\"018f…\",\"name\":\"TR-002\",\"attributes\":{\"campaign\":\"Q3\"}}]`"
  )
  @APIResponse(
    responseCode = "207",
    description =
      "Multi-Status response. Each `results[]` entry carries `index`, `status` " +
      "(`created` or `error`), `appId` (on success), `errorCode` + `errorMessage` (on failure). " +
      "`created` + `failed` == `results.size()`.",
    content = @Content(schema = @Schema(implementation = DataObjectBatchResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Array is empty, exceeds 500 items, or body is not an array.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response batch(
    @RequestBody(
      required = true,
      description =
        "JSON array of DataObject creation requests. " +
        "Must contain 1–500 items. Each item requires `collectionAppId` and `name`.",
      content = @Content(
        schema = @Schema(
          type = SchemaType.ARRAY,
          implementation = DataObjectBatchCreateItemIO.class
        )
      )
    ) List<DataObjectBatchCreateItemIO> items,
    @Context SecurityContext sc
  ) {
    // ── auth gate ──────────────────────────────────────────────────────────
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem("/problems/data-objects.unauthorized", "Authentication required",
        Response.Status.UNAUTHORIZED, "Authentication is required to submit a batch create request.");

    // ── size validation ────────────────────────────────────────────────────
    if (items == null || items.isEmpty()) {
      return problem("/problems/data-objects.batch.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "Batch must contain at least 1 item.");
    }
    if (items.size() > MAX_BATCH_SIZE) {
      return problem("/problems/data-objects.batch.bad-request", "Bad Request", Response.Status.BAD_REQUEST,
          "Batch size " + items.size() + " exceeds the maximum of " + MAX_BATCH_SIZE + " items.");
    }

    // ── process items sequentially ─────────────────────────────────────────
    // Permission memo: avoid re-checking the same Collection on every item.
    // Key: collectionOgmId → Boolean (true = Write allowed)
    Map<Long, Boolean> permCache = new HashMap<>();

    List<DataObjectBatchResultItemIO> results = new ArrayList<>(items.size());
    int createdCount = 0;
    int failedCount = 0;

    for (int i = 0; i < items.size(); i++) {
      DataObjectBatchCreateItemIO item = items.get(i);

      // ── per-item input validation ────────────────────────────────────────
      if (item == null) {
        results.add(DataObjectBatchResultItemIO.error(i, "INVALID_INPUT", "Item at index " + i + " is null."));
        failedCount++;
        continue;
      }
      if (item.getCollectionAppId() == null || item.getCollectionAppId().isBlank()) {
        results.add(DataObjectBatchResultItemIO.error(i, "INVALID_INPUT", "collectionAppId is required."));
        failedCount++;
        continue;
      }
      if (item.getName() == null || item.getName().isBlank()) {
        results.add(DataObjectBatchResultItemIO.error(i, "INVALID_INPUT", "name is required and must not be blank."));
        failedCount++;
        continue;
      }

      // ── resolve collection ───────────────────────────────────────────────
      Long collectionOgmId = resolveOrNull(item.getCollectionAppId());
      if (collectionOgmId == null) {
        results.add(DataObjectBatchResultItemIO.error(
          i, "COLLECTION_NOT_FOUND",
          "No Collection found with appId: " + item.getCollectionAppId()
        ));
        failedCount++;
        continue;
      }

      // ── permission check (memoised) ──────────────────────────────────────
      boolean writeAllowed = permCache.computeIfAbsent(
        collectionOgmId,
        id -> permissionsService.isAccessTypeAllowedForUser(id, AccessType.Write, caller)
      );
      if (!writeAllowed) {
        results.add(DataObjectBatchResultItemIO.error(
          i, "FORBIDDEN",
          "Caller lacks Write permission on Collection: " + item.getCollectionAppId()
        ));
        failedCount++;
        continue;
      }

      // ── resolve optional parentAppId ─────────────────────────────────────
      Long parentOgmId = null;
      if (item.getParentAppId() != null && !item.getParentAppId().isBlank()) {
        parentOgmId = resolveOrNull(item.getParentAppId());
        if (parentOgmId == null) {
          results.add(DataObjectBatchResultItemIO.error(
            i, "PARENT_NOT_FOUND",
            "No DataObject found with parentAppId: " + item.getParentAppId()
          ));
          failedCount++;
          continue;
        }
      }

      // ── build DataObjectIO payload for the service ───────────────────────
      DataObjectIO body = new DataObjectIO();
      body.setName(item.getName());
      body.setDescription(item.getDescription());
      body.setAttributes(item.getAttributes());
      body.setStatus(item.getStatus());
      if (parentOgmId != null) {
        body.setParentId(parentOgmId);
      }

      // ── delegate to service ──────────────────────────────────────────────
      try {
        var created = dataObjectService.createDataObject(collectionOgmId, body);
        results.add(DataObjectBatchResultItemIO.success(i, created.getAppId()));
        createdCount++;
      } catch (InvalidAuthException e) {
        // Service-level auth check — belt-and-braces after our memo check.
        results.add(DataObjectBatchResultItemIO.error(i, "FORBIDDEN", e.getMessage()));
        failedCount++;
      } catch (InvalidPathException e) {
        results.add(DataObjectBatchResultItemIO.error(i, "COLLECTION_NOT_FOUND", e.getMessage()));
        failedCount++;
      } catch (InvalidBodyException e) {
        results.add(DataObjectBatchResultItemIO.error(i, "INVALID_INPUT", e.getMessage()));
        failedCount++;
      } catch (ShepardException e) {
        // Any other Shepard-typed exception (e.g. InvalidRequestException).
        results.add(DataObjectBatchResultItemIO.error(i, "INVALID_INPUT", e.getMessage()));
        failedCount++;
      } catch (Exception e) {
        Log.warnf("Batch DataObject create: unexpected error at index %d — %s", i, e.getMessage());
        results.add(DataObjectBatchResultItemIO.error(i, "INTERNAL_ERROR",
          "Unexpected error: " + e.getMessage()));
        failedCount++;
      }
    }

    DataObjectBatchResponseIO response = new DataObjectBatchResponseIO(createdCount, failedCount, results);
    // HTTP 207 Multi-Status — always, even on all-failed batches.
    return Response.status(207).entity(response).build();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Long resolveOrNull(String appId) {
    try {
      return entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      return null;
    }
  }
}
