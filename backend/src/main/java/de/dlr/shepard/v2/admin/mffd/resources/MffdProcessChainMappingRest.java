package de.dlr.shepard.v2.admin.mffd.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.common.ProblemResponse;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.admin.mffd.io.ProcessChainMappingResultIO;
import de.dlr.shepard.v2.admin.mffd.services.MffdProcessChainMappingService;
import de.dlr.shepard.v2.admin.mffd.services.MffdProcessChainMappingService.InvalidMappingPayloadException;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * MFFD-MAPPING-REST-1 — admin REST surface for the cross-process
 * Predecessor edge loader.
 *
 * <p>Lives under {@code /v2/admin/mffd/process-chain-mapping} —
 * exclusively {@code @RolesAllowed("instance-admin")}. Consumes
 * {@code text/yaml} / {@code application/yaml} / {@code text/plain};
 * produces {@code application/json} (RFC 7807 problem+json on errors).
 *
 * <p>The handler records a richer {@code :Activity} (action=EXECUTE,
 * target=MffdProcessChainMapping) carrying the counters in the
 * summary and signals {@link ProvenanceCaptureFilter} to skip its
 * generic capture so the audit trail has exactly one row per apply.
 *
 * @see MffdProcessChainMappingService
 * @see <a href="../../../../../../../../../aidocs/integrations/118-mffd-process-chain-mapping.md">design doc</a>
 */
@Path("/v2/admin/mffd/process-chain-mapping")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class MffdProcessChainMappingRest {

  /** RFC 7807 type URI for malformed YAML payloads. */
  static final String PROBLEM_TYPE_INVALID_PAYLOAD = "/problems/mffd.process-chain-mapping.invalid-payload";

  static final String MEDIA_TYPE_YAML = "text/yaml";
  static final String MEDIA_TYPE_APPLICATION_YAML = "application/yaml";

  @Inject
  MffdProcessChainMappingService service;

  @Inject
  ProvenanceService provenanceService;

  /**
   * Injected so the handler can set
   * {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE} after recording
   * its own {@code :Activity}, preventing a duplicate generic row from
   * the response filter (CLAUDE.md §"handlers that record their own
   * Activity hand off skip-capture").
   */
  @Context
  ContainerRequestContext requestContext;

  @POST
  @Consumes({ MEDIA_TYPE_YAML, MEDIA_TYPE_APPLICATION_YAML, MediaType.TEXT_PLAIN })
  @Operation(
    operationId = "apply",
    summary = "Apply a MFFD process-chain mapping YAML payload (admin-only).",
    description = "Parses the YAML body, matches source and target DataObjects via " +
    "their urn:shepard:mffd:* SemanticAnnotation predicates, and MERGEs " +
    "(s)-[r:has_successor]->(t) edges between each (source × target) pair. " +
    "The MERGE is idempotent: re-running converges; mutating an entry's " +
    "transitionKind and re-running updates the existing edge. The loader " +
    "never deletes edges. Records a single :Activity (EXECUTE / MffdProcessChainMapping) " +
    "with the matched / unmatched / edgesCreated counters in the summary."
  )
  @APIResponse(
    responseCode = "200",
    description = "Mapping applied — counters + unresolved checklist.",
    content = @Content(schema = @Schema(implementation = ProcessChainMappingResultIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Malformed YAML or unsupported schemaVersion (RFC 7807).",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response apply(String yamlBody, @Context SecurityContext sc) {
    long startedAtMillis = System.currentTimeMillis();

    ProcessChainMappingResultIO result;
    try {
      result = service.apply(yamlBody);
    } catch (InvalidMappingPayloadException e) {
      Log.warnf("MFFD process-chain mapping: rejected payload (%s)", e.getMessage());
      return problem(
        PROBLEM_TYPE_INVALID_PAYLOAD,
        "Invalid MFFD process-chain mapping payload",
        Status.BAD_REQUEST,
        e.getMessage()
      );
    }

    recordActivity(sc, result, startedAtMillis);
    return Response.ok(result).build();
  }

  /**
   * Record a richer Activity covering the apply, then signal the
   * {@link ProvenanceCaptureFilter} to skip its generic capture. Per
   * CLAUDE.md §"handlers that record their own Activity hand off
   * skip-capture": pair the two; either record + skip, or do neither.
   */
  void recordActivity(SecurityContext sc, ProcessChainMappingResultIO result, long startedAtMillis) {
    long endedAtMillis = System.currentTimeMillis();
    String caller = sc == null || sc.getUserPrincipal() == null ? "system" : sc.getUserPrincipal().getName();
    String summary = String.format(
      "Applied MFFD process-chain mapping (schemaVersion=%d, entries=%d, matched=%d, unmatched=%d, edgesCreated=%d)",
      result.getSchemaVersion(),
      result.getEntries(),
      result.getMatched(),
      result.getUnmatched(),
      result.getEdgesCreated()
    );
    try {
      provenanceService.record(
        "EXECUTE",
        "MffdProcessChainMapping",
        null,
        caller,
        summary,
        "POST",
        "v2/admin/mffd/process-chain-mapping",
        200,
        startedAtMillis,
        endedAtMillis
      );
    } catch (RuntimeException e) {
      Log.debugf(e, "MFFD process-chain mapping: provenance capture skipped");
    } finally {
      // Skip-capture handoff to ProvenanceCaptureFilter — always paired
      // with a record() call, never absent.
      try {
        if (requestContext != null) {
          requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
        }
      } catch (RuntimeException ignored) { /* best-effort */ }
    }
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private Response problem(String type, String title, Status status, String detail) {
    return ProblemResponse.problem(type, title, status, detail);
  }
}
