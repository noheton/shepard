package de.dlr.shepard.v2.quality.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.quality.io.CreateDQRIO;
import de.dlr.shepard.v2.quality.io.DQRIO;
import de.dlr.shepard.v2.quality.io.DQRResultIO;
import de.dlr.shepard.v2.quality.io.DQRResultsIO;
import de.dlr.shepard.v2.quality.services.DataQualityRequirementService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * TPL10 — Data Quality Requirements REST surface for Collections.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /v2/collections/{collectionAppId}/dqr}             — list DQRs (Read).</li>
 *   <li>{@code POST   /v2/collections/{collectionAppId}/dqr}             — assign DQR (Write).</li>
 *   <li>{@code DELETE /v2/collections/{collectionAppId}/dqr/{appId}}  — remove DQR (Write).</li>
 *   <li>{@code POST   /v2/collections/{collectionAppId}/dqr/evaluate}    — evaluate DQRs (Read).</li>
 * </ul>
 *
 * <p>All endpoints require authentication. Permission requirements are enforced
 * by {@link DataQualityRequirementService}.
 */
@Path("/v2/collections/{collectionAppId}/dqr")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Data quality")
public class CollectionDQRRest {

  static final String PT_UNAUTHORIZED = "/problems/dqr.unauthorized";

  @Inject
  DataQualityRequirementService service;

  // ─── List ─────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "listDataQualityRequirements",
    summary = "List DQRs assigned to this Collection (TPL10).",
    description =
      "Returns all Data Quality Requirements that have been assigned to this Collection " +
      "via the APPLIES_TO relationship. Results are unordered and unfiltered — include " +
      "both enabled and disabled DQRs.\n\n" +
      "Pagination (APISIMP-PAGINATION-LIST-DQR): supply both `page` (0-based) and " +
      "`pageSize` (1–200) to receive a slice. Omit both to return all DQRs. " +
      "`X-Total-Count` header carries the total DQR count before paging.\n\n" +
      "Auth: Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Header X-Total-Count = total count before paging (kept during deprecation window, APISIMP-PAGINATION-ENVELOPE).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response list(
    @PathParam("collectionAppId") String collectionAppId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    long skip = (long) page * pageSize;
    PagedResponseIO<DQRIO> result = service.list(collectionAppId, caller, skip, pageSize);
    return Response.ok(result)
        .build();
  }

  // ─── Assign ───────────────────────────────────────────────────────────────

  @POST
  @Operation(
    operationId = "assign",
    summary = "Assign a new DQR to this Collection (TPL10).",
    description =
      "Creates a new Data Quality Requirement node and attaches it to the Collection " +
      "via an APPLIES_TO relationship. The DQR applies to all DataObjects in the Collection " +
      "when evaluation is triggered.\n\n" +
      "Supported ruleTypes:\n" +
      "- `ANNOTATION_REQUIRED` — DataObject must have a non-null attribute for the key in `ruleParam`. **Implemented.**\n" +
      "- `NO_TIMESERIES_GAP`   — no timeseries gap > `ruleParam` seconds. **Stub (always PASS).**\n" +
      "- `FILE_COUNT_MIN`      — file container must have >= `ruleParam` files. **Stub (always PASS).**\n" +
      "- `CUSTOM_CYPHER`       — arbitrary Cypher predicate. **Stub (always PASS).**\n\n" +
      "Auth: Write permission on the Collection."
  )
  @APIResponse(
    responseCode = "201",
    description = "DQR created and assigned.",
    content = @Content(schema = @Schema(implementation = DQRIO.class))
  )
  @APIResponse(responseCode = "400", description = "Validation error — bad ruleType or missing required field.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response assign(
    @PathParam("collectionAppId") String collectionAppId,
    @Valid CreateDQRIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    DQRIO result = service.assign(collectionAppId, body, caller);
    return Response.status(Response.Status.CREATED).entity(result).build();
  }

  // ─── Remove ───────────────────────────────────────────────────────────────

  @DELETE
  @Path("{appId}")
  @Operation(
    operationId = "remove",
    summary = "Remove a DQR from this Collection (TPL10).",
    description =
      "Deletes the Data Quality Requirement node and detaches all its relationships. " +
      "Returns 404 when the DQR does not exist or is not assigned to this Collection.\n\n" +
      "Auth: Write permission on the Collection."
  )
  @APIResponse(responseCode = "204", description = "DQR removed.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "No such DQR or not assigned to this Collection.")
  public Response remove(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("appId") String appId,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    service.remove(collectionAppId, appId, caller);
    return Response.noContent().build();
  }

  // ─── Evaluate ─────────────────────────────────────────────────────────────

  @POST
  @Path("evaluate")
  @Operation(
    operationId = "evaluate",
    summary = "Evaluate all enabled DQRs for this Collection (TPL10).",
    description =
      "Runs every enabled Data Quality Requirement assigned to this Collection and " +
      "returns one result per (DQR, DataObject) pair. A result with `passed == false` " +
      "carries a human-readable `message` describing the violation.\n\n" +
      "Only DQRs with `enabled == true` are evaluated. Disabled DQRs are silently skipped.\n\n" +
      "Results are capped at `limit` (default 5 000, max 5 000). When the cap is hit, " +
      "`truncated: true` is set in the envelope and `total` carries the uncapped count.\n\n" +
      "Evaluation is synchronous and may be slow on large Collections. A future version " +
      "will add an async variant.\n\n" +
      "Auth: Read permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Evaluation results envelope. `truncated` is true when the result cap was applied.",
    content = @Content(schema = @Schema(implementation = DQRResultsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection with that appId.")
  public Response evaluate(
    @PathParam("collectionAppId") String collectionAppId,
    @Parameter(description = "Maximum results to return, 1–5000 (default 5000).")
    @QueryParam("maxItems") @DefaultValue("5000") @Min(1) @Max(5000) int maxItems,
    @Context SecurityContext securityContext
  ) {
    String caller = caller(securityContext);
    if (caller == null) return unauthorized();
    List<DQRResultIO> all = service.evaluate(collectionAppId, caller);
    long total = all.size();
    boolean truncated = total > maxItems;
    List<DQRResultIO> results = truncated ? all.subList(0, maxItems) : all;
    return Response.ok(new DQRResultsIO(results, truncated, total)).build();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private static String caller(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return problem(PT_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "Authentication is required to access data quality requirements.");
  }

}
