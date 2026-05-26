package de.dlr.shepard.v2.importer.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import de.dlr.shepard.v2.importer.entities.ImportLock;
import de.dlr.shepard.v2.importer.entities.ImportPlan;
import de.dlr.shepard.v2.importer.io.ImportJobRequestIO;
import de.dlr.shepard.v2.importer.io.ImportJobResultIO;
import de.dlr.shepard.v2.importer.services.ImportExecutionService;
import de.dlr.shepard.v2.importer.services.ImportLockService;
import de.dlr.shepard.v2.importer.services.ImportValidationService;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * IMP2 — {@code POST /v2/import/jobs}: execute a previously-validated import plan.
 *
 * <p>The caller presents a {@code commitId} (plan seal) that was issued by
 * {@code POST /v2/import/validate}.  This endpoint:
 * <ol>
 *   <li>Resolves the plan — 404 if unknown.</li>
 *   <li>Checks status: {@code USED → 409}, {@code EXPIRED → 410},
 *       {@code INVALIDATED → 422}, {@code null manifestJson → 410} (pre-IMP2 plan).</li>
 *   <li>Re-checks {@code expiresAt &lt; now} — 410 if stale.</li>
 *   <li>Verifies Write access on the target Collection — 403 if missing.</li>
 *   <li>Re-verifies the collection fingerprint — 409 if the collection changed
 *       since validation.</li>
 *   <li>Acquires the import lock — 409 if a fresh concurrent import is running.</li>
 *   <li>Executes the manifest via {@link ImportExecutionService}.</li>
 *   <li>Releases the lock and marks the plan {@code USED} (even on
 *       {@code PARTIAL_FAILURE} — commitId is one-shot).</li>
 *   <li>Returns 201 ({@code COMPLETED}) or 207 ({@code PARTIAL_FAILURE}).</li>
 * </ol>
 */
@Path("/v2/import/jobs")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Import (IMP2)")
public class ImportJobsV2Rest {

  @Inject
  ImportPlanDAO importPlanDAO;

  @Inject
  ImportValidationService validationService;

  @Inject
  ImportExecutionService executionService;

  @Inject
  ImportLockService lockService;

  @Inject
  PermissionsService permissionsService;

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  // ─── POST /v2/import/jobs ─────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Execute a validated import plan (IMP2)",
    description =
      "Consumes the commitId issued by POST /v2/import/validate and runs the import. " +
      "Returns 201 on full success or 207 on partial failure (some entities failed to create). " +
      "The commitId is one-shot — once executed (or attempted), the plan is marked USED. " +
      "Returns 409 if the plan was already used, if a concurrent import is running, or if " +
      "the collection changed since the plan was validated. " +
      "Returns 410 if the plan expired (TTL 24 h) or is a pre-IMP2 plan (no stored manifest). " +
      "Returns 422 if the plan was INVALIDATED (had hard validation errors)."
  )
  @APIResponse(
    responseCode = "201",
    description = "Import completed — all entities created.",
    content = @Content(schema = @Schema(implementation = ImportJobResultIO.class))
  )
  @APIResponse(
    responseCode = "207",
    description = "Partial failure — some entities failed; result body contains details.",
    content = @Content(schema = @Schema(implementation = ImportJobResultIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the target Collection.")
  @APIResponse(responseCode = "404", description = "No plan with this commitId.")
  @APIResponse(responseCode = "409",
    description = "Plan already USED, collection changed since validation, or concurrent import running.")
  @APIResponse(responseCode = "410",
    description = "Plan expired (TTL exceeded) or is a pre-IMP2 plan (no stored manifest).")
  @APIResponse(responseCode = "422",
    description = "Plan was INVALIDATED — validation had hard errors; no import was run.")
  public Response executeImport(
    @Valid ImportJobRequestIO request,
    @Context SecurityContext sc
  ) {
    String caller = caller(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    // ── 1. Resolve plan ─────────────────────────────────────────────────────
    ImportPlan plan = importPlanDAO.findByCommitId(request.commitId());
    if (plan == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No plan found for commitId: " + request.commitId())
        .build();
    }

    // ── 2. Status checks ────────────────────────────────────────────────────
    switch (plan.getStatus()) {
      case "USED" -> {
        return Response.status(Response.Status.CONFLICT)
          .entity("Plan already executed (status=USED): " + request.commitId())
          .build();
      }
      case "INVALIDATED" -> {
        return Response.status(422)
          .entity("Plan was INVALIDATED — cannot execute a plan with hard validation errors.")
          .build();
      }
      case "EXPIRED" -> {
        return Response.status(Response.Status.GONE)
          .entity("Plan has expired (status=EXPIRED): " + request.commitId())
          .build();
      }
      default -> { /* VALID — continue */ }
    }

    // ── 3. Re-check expiresAt ────────────────────────────────────────────────
    if (plan.getExpiresAt() != null && plan.getExpiresAt() < System.currentTimeMillis()) {
      plan.setStatus("EXPIRED");
      importPlanDAO.createOrUpdate(plan);
      return Response.status(Response.Status.GONE)
        .entity("Plan has expired (TTL exceeded): " + request.commitId())
        .build();
    }

    // ── 4. Pre-IMP2 plan guard (manifestJson == null) ───────────────────────
    if (plan.getManifestJson() == null) {
      return Response.status(Response.Status.GONE)
        .entity("Plan predates IMP2 (no stored manifest) — re-validate to obtain a " +
          "new commitId: " + request.commitId())
        .build();
    }

    // ── 5. Write permission on target Collection ─────────────────────────────
    Optional<Long> collOgmId = collectionPropertiesDAO.findCollectionIdByAppId(
      plan.getCollectionAppId());
    if (collOgmId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("Target collection not found: " + plan.getCollectionAppId())
        .build();
    }
    if (!permissionsService.isAccessTypeAllowedForUser(
        collOgmId.get(), AccessType.Write, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // ── 6. Fingerprint re-verification ───────────────────────────────────────
    String currentRaw = importPlanDAO.getRawCollectionFingerprintInput(plan.getCollectionAppId());
    String currentFingerprint = sha256hex(currentRaw);
    if (!currentFingerprint.equals(plan.getCollectionFingerprint())) {
      Log.warnf("IMP2: collection fingerprint mismatch for plan %s — collection has changed " +
        "since validation. expected=%s actual=%s",
        request.commitId(), plan.getCollectionFingerprint(), currentFingerprint);
      return Response.status(Response.Status.CONFLICT)
        .entity("Collection has changed since this plan was validated — " +
          "re-validate to obtain a fresh commitId.")
        .build();
    }

    // ── 7. Import lock ────────────────────────────────────────────────────────
    ImportLock lock = lockService.acquire(plan.getCollectionAppId(), caller);
    if (lock == null) {
      return Response.status(Response.Status.CONFLICT)
        .entity("A concurrent import is already running for this collection — try again later.")
        .build();
    }

    // ── 8. Execute ────────────────────────────────────────────────────────────
    ImportJobResultIO result;
    try {
      result = executionService.execute(plan, collOgmId.get());
    } catch (Exception e) {
      Log.errorf("IMP2: unexpected error executing plan %s: %s", request.commitId(), e.getMessage());
      lockService.abandon(lock.getLockId(), e.getMessage());
      // Mark plan USED even on unexpected error — commitId is one-shot.
      plan.setStatus("USED");
      importPlanDAO.createOrUpdate(plan);
      return Response.serverError()
        .entity("Import execution failed unexpectedly: " + e.getMessage())
        .build();
    } finally {
      // Lock release is best-effort in the finally block.
      // The normal release path is called below; if an exception bypasses that,
      // abandon handles it (idempotent — release on an already-terminated lock is a no-op).
    }

    // ── 9. Release lock + mark plan USED ─────────────────────────────────────
    lockService.release(lock.getLockId());
    plan.setStatus("USED");
    importPlanDAO.createOrUpdate(plan);

    // ── 10. Response ──────────────────────────────────────────────────────────
    if ("COMPLETED".equals(result.status())) {
      return Response.status(Response.Status.CREATED).entity(result).build();
    } else {
      // HTTP 207 Multi-Status for partial failure.
      return Response.status(207).entity(result).build();
    }
  }

  // ─── Private helpers ──────────────────────────────────────────────────────

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * Compute SHA-256 hex of a string.
   *
   * <p>Mirrors the private helper in {@link ImportValidationService} so the
   * fingerprint comparison is consistent (both use UTF-8, no prefix).
   *
   * @param input the input string
   * @return lowercase hex-encoded SHA-256 digest
   */
  static String sha256hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
