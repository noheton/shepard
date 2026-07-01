package de.dlr.shepard.v2.admin.ledger;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.ledger.io.LedgerAnchorJobIO;
import de.dlr.shepard.v2.admin.ledger.io.LedgerAnchorRequestIO;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Admin endpoints for distributed-ledger anchoring of {@code prov:Activity} records.
 *
 * <p>Designed in {@code aidocs/integrations/111-tpl17-distributed-ledger-anchoring.md}.
 * Provides tamper-evidence on top of the HMAC chain (§5 of that doc):
 * a SHA-256 digest of each Activity's JSON-LD serialisation is written to an
 * external blockchain so even the Shepard operator cannot alter a committed entry.
 *
 * <p><b>Feature-gated</b>: this bean is only instantiated when
 * {@code shepard.ledger.enabled=true}.  With the default {@code false} value, CDI
 * does not register the bean and JAX-RS returns 404 for all paths in this class —
 * callers must check feature availability before assuming the endpoints exist.
 *
 * <p><b>Phase 1</b> (this slice) ships the REST skeleton only.  All methods return
 * {@code 501 Not Implemented} until the Bloxberg client (TPL17a) is wired.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/ledger")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
@LookupIfProperty(name = "shepard.ledger.enabled", stringValue = "true", lookupIfMissing = false)
public class LedgerAnchorRest {

  private static final String PT_NOT_IMPLEMENTED = "/problems/ledger.not-implemented";

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }

  // -----------------------------------------------------------------------
  // POST /v2/admin/ledger/anchor
  // -----------------------------------------------------------------------

  @POST
  @Path("/anchor")
  @Operation(
    operationId = "anchor",
    summary = "Anchor Activity records on a distributed ledger.",
    description = "Computes a SHA-256 digest of each requested Activity's JSON-LD " +
    "serialisation and submits it to the configured ledger provider (Bloxberg or " +
    "OpenTimestamps). Returns a jobId immediately; poll GET /anchor/{jobId} for status. " +
    "Requires instance-admin. Feature must be enabled via shepard.ledger.enabled=true."
  )
  @APIResponse(
    responseCode = "202",
    description = "Anchor job accepted and queued.",
    content = @Content(schema = @Schema(implementation = LedgerAnchorJobIO.class))
  )
  @APIResponse(responseCode = "400", description = "Invalid request body.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "501", description = "Ledger client not yet implemented (Phase 1 skeleton).")
  public Response anchor(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = LedgerAnchorRequestIO.class))
    ) @Valid LedgerAnchorRequestIO body
  ) {
    return problem(PT_NOT_IMPLEMENTED, "Not Implemented",
      Status.NOT_IMPLEMENTED,
      "Ledger anchor client not yet implemented (TPL17a). " +
      "See aidocs/integrations/111-tpl17-distributed-ledger-anchoring.md.");
  }

  // -----------------------------------------------------------------------
  // GET /v2/admin/ledger/anchor/{jobId}
  // -----------------------------------------------------------------------

  @GET
  @Path("/anchor/{jobId}")
  @Operation(
    operationId = "getJob",
    summary = "Poll an anchor job by jobId.",
    description = "Returns current status (queued | running | complete | failed) and a " +
    "human-readable summary.  Poll until status is 'complete' or 'failed'."
  )
  @APIResponse(
    responseCode = "200",
    description = "Job status.",
    content = @Content(schema = @Schema(implementation = LedgerAnchorJobIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "No job found for the given jobId.")
  @APIResponse(responseCode = "501", description = "Ledger client not yet implemented (Phase 1 skeleton).")
  public Response getJob(@PathParam("jobId") String jobId) {
    return problem(PT_NOT_IMPLEMENTED, "Not Implemented",
      Status.NOT_IMPLEMENTED, "Ledger anchor job store not yet implemented (TPL17a).");
  }

  // -----------------------------------------------------------------------
  // GET /v2/data-objects/{appId}/ledger-anchors
  // Note: path diverges from the /v2/admin prefix because auditors query
  //       by DataObject, not by admin concern.  The endpoint still requires
  //       instance-admin in Phase 1; access-control relaxation (to owner /
  //       reviewer) is a Phase 2 enhancement.
  // -----------------------------------------------------------------------

  @GET
  @Path("/data-objects/{appId}/ledger-anchors")
  @Operation(
    operationId = "getAnchorsForDataObject",
    summary = "List ledger anchors for a DataObject.",
    description = "Returns all Activity nodes linked to the given DataObject that carry " +
    "a non-null ledgerAnchor field.  Useful for auditors who need to verify tamper " +
    "evidence for a specific DataObject without knowing Activity appIds in advance."
  )
  @APIResponse(responseCode = "200", description = "List of anchored Activity records for the DataObject.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "No DataObject found with the given appId.")
  @APIResponse(responseCode = "501", description = "Ledger query not yet implemented (Phase 1 skeleton).")
  public Response getAnchorsForDataObject(@PathParam("appId") String appId) {
    return problem(PT_NOT_IMPLEMENTED, "Not Implemented",
      Status.NOT_IMPLEMENTED, "Ledger anchor query not yet implemented (TPL17a).");
  }
}
