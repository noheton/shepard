package de.dlr.shepard.v2.krl.resources;

import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.v2.krl.io.KrlInterpretRequestIO;
import de.dlr.shepard.v2.krl.io.KrlInterpretResponseIO;
import de.dlr.shepard.v2.krl.services.KrlInterpretService;
import de.dlr.shepard.v2.krl.services.KrlSidecarClient;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * KRL-INTERPRETER-05 — REST surface for the KRL interpret operation.
 *
 * <h2>Endpoint set</h2>
 * <pre>
 *   POST /v2/krl/interpret  — interpret a .src against a URDF, persist
 *                             the resulting joint-trajectory as a new
 *                             TimeseriesReference, return the appId
 *                             + activity appId + warnings + stats.
 * </pre>
 *
 * <h2>Auth</h2>
 * <p>{@code @Authenticated} — any logged-in user. The
 * {@code targetDataObjectAppId} the trajectory attaches to carries its
 * own collection-permission check via
 * {@link de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService#createReference}
 * (returns {@code 403} when the caller lacks write on the owning
 * collection — the same shape every other v2 reference-create path
 * uses).
 *
 * <h2>Provenance</h2>
 * <p>Every successful interpret records a {@code :KrlInterpretActivity}
 * (a {@code :Activity} carrying the supplementary
 * {@code :KrlInterpretActivity} label and the KRL-specific properties
 * documented in
 * {@code aidocs/integrations/117-krl-interpreter.md §7.1}). The handler
 * sets {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE} after the
 * service-layer record() call so the generic capture filter does not
 * emit a duplicate row, per the "handlers that record their own
 * Activity hand off skip-capture" rule.
 *
 * <h2>Error mapping (§7.3)</h2>
 * <pre>
 *   sidecar 200       → 201 Created   trajectory ready
 *   sidecar 4xx       → 400 BadReq    malformed input
 *   sidecar 422       → 422 Unproc.   IK divergence
 *   sidecar 501       → 501 NotImpl   hard-stop construct present
 *   sidecar 5xx       → 502 BadGw     sidecar down / errored
 *   sidecar timeout   → 504 Gw timeout
 * </pre>
 *
 * <p>Until the sidecar (KRL-INTERPRETER-04, in flight in a parallel
 * worktree) is brought up on the operator's compose profile, this
 * endpoint legitimately returns 502 — that is the documented expected
 * behaviour and is verified by {@link
 * de.dlr.shepard.v2.krl.resources.KrlInterpretRestTest}.
 */
@Path("/v2/krl")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "KRL interpreter (v2)")
public class KrlInterpretRest {

  static final String HEADER_AI_AGENT = "X-AI-Agent";

  @Inject KrlInterpretService service;

  @Context jakarta.ws.rs.container.ContainerRequestContext requestContext;

  @POST
  @Path("/interpret")
  @Operation(
    summary = "Interpret a KRL .src program against a URDF and persist the resulting joint trajectory.",
    description =
      "Resolves the `srcFileAppId` and `urdfFileAppId` FileReferences to byte payloads, " +
      "calls the KRL interpreter sidecar at the configured `shepard.krl.sidecar.url`, " +
      "persists the resulting joint-angle trajectory as a new `TimeseriesReference` " +
      "(channels named `joint_0 … joint_N`) under the supplied `targetDataObjectAppId`, " +
      "and records a `:KrlInterpretActivity` PROV-O activity with USED edges to the " +
      "source / URDF / scene / dat FileReferences and a GENERATED edge to the trajectory.\n\n" +
      "Honours `X-AI-Agent`: when present, the recorded Activity carries `sourceMode=ai` " +
      "and `agentId=<header value>` per the EU AI Act Art. 50 disclosure shape.\n\n" +
      "**Sidecar dependency.** The KRL interpreter sidecar (`shepard-plugin-krl-interpreter`) " +
      "is an operator opt-in — when the sidecar is not running, this endpoint returns " +
      "**502 Bad Gateway**. The sidecar lands via `KRL-INTERPRETER-04` (compose profile)."
  )
  @APIResponse(
    responseCode = "201",
    description = "Trajectory persisted; trajectory + activity appIds returned.",
    content = @Content(schema = @Schema(implementation = KrlInterpretResponseIO.class))
  )
  @APIResponse(responseCode = "400", description = "Malformed input (missing field, unknown appId).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks write on the target DataObject's collection.")
  @APIResponse(responseCode = "409", description = "Duplicate timeseries data points detected; re-send with overwrite=true to replace.")
  @APIResponse(responseCode = "422", description = "IK divergence above threshold.")
  @APIResponse(responseCode = "501", description = "KRL construct in HARD-STOP list (SPS / INTERRUPT / ANIN / ANOUT).")
  @APIResponse(responseCode = "502", description = "Sidecar unreachable or returned a non-2xx response.")
  @APIResponse(responseCode = "504", description = "Sidecar call timed out.")
  public Response interpret(
    KrlInterpretRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @QueryParam("overwrite") @DefaultValue("false") boolean overwrite,
    @Context SecurityContext sc
  ) {
    String username = sc != null && sc.getUserPrincipal() != null
      ? sc.getUserPrincipal().getName() : null;
    try {
      KrlInterpretResponseIO response = service.interpret(body, username, aiAgent, overwrite);
      handoffProvenance();
      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(errorBody(bre.getMessage())).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(errorBody(nfe.getMessage())).build();
    } catch (KrlInterpretService.SidecarException ex) {
      return mapSidecarException(ex);
    } catch (RuntimeException ex) {
      // Permission failures from TimeseriesReferenceService land here as
      // InvalidAuthException — map to 403; other RuntimeExceptions bubble
      // as 500 so they show up loudly during dev.
      String type = ex.getClass().getSimpleName();
      if (type.contains("Auth")) {
        return Response.status(Response.Status.FORBIDDEN).entity(errorBody(ex.getMessage())).build();
      }
      Log.errorf(ex, "KRL: unexpected error in /v2/krl/interpret");
      throw ex;
    }
  }

  private static Response mapSidecarException(KrlInterpretService.SidecarException ex) {
    KrlSidecarClient.SidecarOutcome outcome = ex.getOutcome();
    switch (outcome.status()) {
      case TIMEOUT -> {
        return Response.status(504).entity(errorBody(outcome.errorDetail())).build();
      }
      case UNREACHABLE -> {
        return Response.status(502).entity(errorBody(outcome.errorDetail())).build();
      }
      case SIDECAR_ERROR -> {
        int sc = outcome.sidecarStatus() == null ? 502 : outcome.sidecarStatus();
        int mapped = switch (sc) {
          case 400, 422, 501 -> sc;
          default -> 502;
        };
        return Response.status(mapped).entity(errorBody(outcome.errorDetail())).build();
      }
      default -> {
        return Response.status(502).entity(errorBody("Unknown sidecar outcome")).build();
      }
    }
  }

  /**
   * Hand off skip-capture to {@link ProvenanceCaptureFilter} so the
   * filter doesn't emit a duplicate generic Activity. Per the
   * "handlers that record their own Activity hand off skip-capture"
   * rule — paired with every successful call to
   * {@link KrlInterpretService#interpret}.
   */
  private void handoffProvenance() {
    try {
      if (requestContext != null) {
        requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
      }
    } catch (RuntimeException e) {
      Log.debug("KRL: skip-capture handoff failed (non-fatal)", e);
    }
  }

  private static String errorBody(String detail) {
    if (detail == null) detail = "Unknown error";
    String safe = detail.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"detail\":\"" + safe + "\"}";
  }
}
