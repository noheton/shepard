package de.dlr.shepard.v2.quality.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.v2.quality.io.IndependenceProofRequestIO;
import de.dlr.shepard.v2.quality.io.IndependenceProofResultIO;
import de.dlr.shepard.v2.quality.services.IndependenceProofService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TPL11 — independence proof endpoint.
 *
 * <p>Checks whether two sets of DataObjects are mutually independent with
 * respect to provenance ancestry and shared annotations. The canonical use
 * case is validating that a training set and a test set used for AI/ML model
 * development do not overlap in their data lineage.
 *
 * <p>Auth: {@code @Authenticated}. The endpoint does not reveal DataObject
 * content — only structural information (shared ancestor appIds) and
 * annotation key-value pairs that are present in both sets. Callers can
 * only reach ancestor appIds that are already accessible from the appIds
 * they submitted. The conservative posture is consistent with other
 * structural / analytics endpoints (e.g. the SPARQL proxy, anomaly detection).
 *
 * <p>Path: {@code POST /v2/quality/independence-proof}
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/quality/independence-proof")
@Authenticated
@RequestScoped
@Tag(name = "Data quality")
public class IndependenceProofRest {

  @Inject
  IndependenceProofService service;

  /**
   * Check whether two DataObject sets are independent.
   *
   * <p>Returns {@code independent: true} when:
   * <ul>
   *   <li>No DataObject in setA and no DataObject in setB share a common
   *       provenance ancestor (within a 10-hop window).</li>
   *   <li>No annotation key-value pair appears on at least one member of
   *       setA and at least one member of setB with the same value.</li>
   * </ul>
   *
   * <p>The check is best-effort: the ancestor walk is bounded at 10 hops.
   * Chains longer than 10 hops are not covered.
   */
  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/independence-proof.bad-request";

  @POST
  @Operation(
    operationId = "check",
    summary = "Check whether two DataObject sets are mutually independent.",
    description =
      "Runs two checks against the two supplied DataObject appId sets:\n\n" +
      "1. **Provenance ancestry** — are there any DataObjects that are " +
      "   common ancestors (within 10 hops) of members in both sets?\n" +
      "2. **Shared annotations** — do any members of setA and setB share an " +
      "   annotation key-value pair (strict equality on both key and value)?\n\n" +
      "`independent: true` is returned only when both checks find no overlap.\n\n" +
      "**Typical use case:** validate that a training set and a test set used " +
      "for AI/ML training do not share data lineage.\n\n" +
      "**Hop cap:** the ancestor walk is bounded at 10 hops. Provenance chains " +
      "longer than 10 levels are not covered by this check — the result is " +
      "best-effort, not a formal mathematical proof.\n\n" +
      "**Auth:** any authenticated user. The endpoint returns only structural " +
      "metadata (ancestor appIds, annotation keys/values) — no DataObject content."
  )
  @APIResponse(
    responseCode = "200",
    description = "Check completed. Inspect `independent` and the two lists for details.",
    content = @Content(schema = @Schema(implementation = IndependenceProofResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Request body missing, setA/setB is null/empty, or either set exceeds 500 elements.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response check(@Valid IndependenceProofRequestIO body) {
    if (body == null) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing request body",
        Response.Status.BAD_REQUEST, "Request body is required.");
    }

    List<String> setA = body.getSetA();
    List<String> setB = body.getSetB();

    if (setA == null || setA.isEmpty()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "setA must contain at least one appId.");
    }
    if (setB == null || setB.isEmpty()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "setB must contain at least one appId.");
    }

    IndependenceProofResultIO result = service.check(body);
    return Response.ok(result).build();
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
