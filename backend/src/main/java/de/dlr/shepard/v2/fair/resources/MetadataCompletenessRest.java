package de.dlr.shepard.v2.fair.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.v2.fair.io.MetadataCompletenessScoreIO;
import de.dlr.shepard.v2.fair.services.MetadataCompletenessService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * FAIR4 — {@code GET /v2/collections/{appId}/metadata-completeness}.
 *
 * <p>Returns a server-authoritative {@link MetadataCompletenessScoreIO} —
 * a 0–100 completeness score with per-check breakdown — that external
 * harvesters (OpenAIRE, Helmholtz Databus) can query without a browser
 * session. The score mirrors the client-side widget at
 * {@code frontend/components/context/collection/MetadataCompletenessCard.vue}
 * (backed by {@code frontend/utils/metadataCompleteness.ts}).
 *
 * <h3>Checks included (9, total 100 pts)</h3>
 * <ul>
 *   <li>{@code name} — 10 pts — collection name non-blank</li>
 *   <li>{@code description} — 15 pts — description ≥ 50 chars</li>
 *   <li>{@code license} — 20 pts — SPDX string set</li>
 *   <li>{@code accessRights} — 10 pts — access-rights value set</li>
 *   <li>{@code creatorOrcid} — 10 pts — creator ORCID present</li>
 *   <li>{@code semanticAnnotation} — 10 pts — ≥ 1 annotation</li>
 *   <li>{@code labJournal} — 5 pts — ≥ 1 lab-journal entry</li>
 *   <li>{@code keywords} — 5 pts — ≥ 1 keyword-predicate annotation</li>
 *   <li>{@code dataObjects} — 15 pts — ≥ 1 DataObject</li>
 * </ul>
 *
 * <h3>Score bands</h3>
 * <ul>
 *   <li>{@code score < 50} — not publication-ready</li>
 *   <li>{@code 50 ≤ score < 80} — missing key FAIR fields</li>
 *   <li>{@code score ≥ 80} — DMP-grade</li>
 * </ul>
 *
 * <h3>Auth</h3>
 * Same as {@code GET /v2/collections/{appId}} — Read permission required.
 * Returns 401 when unauthenticated, 404 when the appId doesn't resolve,
 * 403 when the caller lacks Read access.
 *
 * <p>Cross-references: {@code aidocs/16} FAIR4 row; {@code aidocs/34}
 * additive endpoint entry; {@code aidocs/44} feature matrix. Pure
 * projection — no new entities, no migrations.
 */
@Path("/v2/collections")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "FAIR — Metadata Completeness (v2)")
public class MetadataCompletenessRest {

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @Inject
  CollectionService collectionService;

  @Inject
  MetadataCompletenessService metadataCompletenessService;

  /**
   * Compute the metadata completeness score for the Collection identified
   * by {@code appId}.
   *
   * @param collectionAppId UUID v7 appId of the Collection
   * @param sc              security context for caller identity
   * @return 200 with {@link MetadataCompletenessScoreIO}; 401/403/404 on auth/id errors
   */
  @GET
  @Path("/{appId}/metadata-completeness")
  @Operation(
    summary = "Compute the FAIR metadata completeness score for a Collection.",
    description =
      "Returns a 0–100 completeness score with per-check breakdown. " +
      "The 9 checks (name, description, license, accessRights, creatorOrcid, " +
      "semanticAnnotation, labJournal, keywords, dataObjects) mirror the " +
      "client-side widget in `MetadataCompletenessCard.vue`.\n\n" +
      "**Score bands:** `< 50` = not publication-ready; `50–79` = missing key " +
      "FAIR fields; `≥ 80` = DMP-grade.\n\n" +
      "**Use case:** external harvesters (OpenAIRE, Helmholtz Databus) can " +
      "query this endpoint to filter / surface only DMP-grade Collections " +
      "without requiring a browser session.\n\n" +
      "**Auth:** Read permission on the Collection. `404` for unknown appIds; " +
      "`403` for callers without Read access; `401` when unauthenticated.\n\n" +
      "**No side effects:** pure read projection — no entity writes, no " +
      "migrations required."
  )
  @APIResponse(
    responseCode = "200",
    description = "Completeness score computed successfully.",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(implementation = MetadataCompletenessScoreIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response getMetadataCompleteness(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext sc
  ) {
    // ── 401 ──────────────────────────────────────────────────────────────────
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // ── 404 ──────────────────────────────────────────────────────────────────
    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    // ── 403 ──────────────────────────────────────────────────────────────────
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // ── Load + compute ────────────────────────────────────────────────────────
    Collection collection = collectionService.getCollectionWithDataObjectsAndIncomingReferences(ogmId.get());
    MetadataCompletenessScoreIO score = metadataCompletenessService.compute(collection);

    return Response.ok(score).build();
  }
}
