package de.dlr.shepard.v2.semantic.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService.PredicateStats;
import de.dlr.shepard.v2.semantic.io.PredicateStatsIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SEMA-V6-PRED-UI — read-only per-predicate usage statistics surface, used
 * by the {@code /semantic/predicates/{predicateIri}} frontend page to show
 * "how often is this predicate used, with what values, on which entities?"
 * without forcing every page load through the SPARQL playground.
 *
 * <p>Path: {@code GET /v2/semantic/predicates/{predicateIriBase64}/stats}
 *
 * <p>The predicate IRI is conveyed as <strong>URL-safe Base64</strong>
 * ({@code RFC 4648 §5}, the {@link Base64#getUrlDecoder()} flavour) because
 * IRIs contain characters that JAX-RS path segments handle poorly
 * (colons in URNs, slashes in HTTP URIs). Callers use
 * {@code btoa(iri).replace(/\+/g, '-').replace(/\//g, '_')} (JS) or
 * {@link Base64#getUrlEncoder()} (Java); padding is optional.
 *
 * <p>Two optional query parameters cap the response size:
 * <ul>
 *   <li>{@code topValuesLimit} (default 20) — max distinct object-value rows.</li>
 *   <li>{@code sampleLimit} (default 10) — max sample-entity rows.</li>
 * </ul>
 *
 * <p>Auth: any authenticated user. Predicate usage stats reveal no
 * permission-protected data — only aggregate counts plus entity appIds
 * that are already visible to anyone who can list the entity.
 *
 * <p>Backlog: {@code SEMA-V6-PRED-UI} (placeholder slug
 * {@code semantic-predicate-stats}). Replaces the SPARQL-fallback
 * advertisement that the page previously rendered as a
 * {@code PlaceholderImplStatus} chip.
 */
@Path("/v2/semantic/predicates")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Semantic predicate statistics")
public class SemanticPredicateStatsRest {

  static final String PROBLEM_TYPE_BAD_IRI = "/problems/semantic.predicate.bad-iri";

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  @GET
  @Path("/{predicateIriBase64}/stats")
  @Operation(
    summary = "Per-predicate usage statistics across all :SemanticAnnotation rows.",
    description =
      "Returns aggregate counts and a sample of annotated entities for the given predicate IRI. " +
      "The IRI must be URL-safe Base64 encoded (RFC 4648 §5) in the path because URNs contain " +
      "colons and HTTP IRIs contain slashes — both problematic in JAX-RS path segments. " +
      "Query params `topValuesLimit` (default 20) and `sampleLimit` (default 10) cap the response size. " +
      "Auth: any authenticated user."
  )
  @APIResponse(
    responseCode = "200",
    description = "Usage statistics for the predicate (counts may be 0; lists may be empty).",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PredicateStatsIO.class))
  )
  @APIResponse(responseCode = "400", description = "predicateIriBase64 is missing, malformed, or decodes to a blank IRI.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getPredicateStats(
    @PathParam("predicateIriBase64") String predicateIriBase64,
    @QueryParam("topValuesLimit") @DefaultValue("20")
    @Parameter(description = "Maximum number of distinct object-value rows to return in `topValues` (default: 20).")
    int topValuesLimit,
    @QueryParam("sampleLimit") @DefaultValue("10")
    @Parameter(description = "Maximum number of sample-entity rows to return in `sampleEntities` (default: 10).")
    int sampleLimit
  ) {
    String iri = decodeIri(predicateIriBase64);
    if (iri == null) {
      return badIri(predicateIriBase64);
    }
    PredicateStats stats = semanticAnnotationService.getPredicateStats(iri, topValuesLimit, sampleLimit);
    return Response.ok(toIO(iri, stats)).build();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * URL-safe Base64 decode the path segment into an IRI. Returns {@code null}
   * when the input is missing/empty, isn't valid Base64, decodes to blank, or
   * decodes to non-UTF8 bytes — all three collapse to a 400.
   */
  private static String decodeIri(String b64) {
    if (b64 == null || b64.isBlank()) return null;
    try {
      // Decoder accepts both with-padding and without; URL-safe alphabet is required.
      byte[] decoded = Base64.getUrlDecoder().decode(b64);
      String iri = new String(decoded, StandardCharsets.UTF_8);
      if (iri.isBlank()) return null;
      return iri;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static Response badIri(String received) {
    ProblemJson body = new ProblemJson(
      PROBLEM_TYPE_BAD_IRI,
      "Bad predicate IRI",
      Status.BAD_REQUEST.getStatusCode(),
      "predicateIriBase64='" + (received == null ? "" : received) +
        "' is missing, not URL-safe Base64, or decodes to a blank IRI.",
      null
    );
    return Response.status(Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON)
      .entity(body)
      .build();
  }

  /** Wire-shape mapper from the service-layer carrier. */
  private static PredicateStatsIO toIO(String iri, PredicateStats stats) {
    List<PredicateStatsIO.TopValue> topValues = stats.topValues().stream()
      .map(SemanticPredicateStatsRest::mapTopValue)
      .toList();
    List<PredicateStatsIO.SampleEntity> samples = stats.sampleEntities().stream()
      .map(SemanticPredicateStatsRest::mapSample)
      .toList();
    return new PredicateStatsIO(iri, stats.annotationCount(), topValues, samples);
  }

  private static PredicateStatsIO.TopValue mapTopValue(Map<String, Object> row) {
    Object iri = row.get("objectIri");
    Object label = row.get("objectLabel");
    Object cnt = row.get("count");
    long n = cnt instanceof Number num ? num.longValue() : 0L;
    return new PredicateStatsIO.TopValue(
      iri == null ? null : iri.toString(),
      label == null ? null : label.toString(),
      n
    );
  }

  private static PredicateStatsIO.SampleEntity mapSample(Map<String, Object> row) {
    Object appId = row.get("appId");
    Object name = row.get("name");
    Object type = row.get("type");
    return new PredicateStatsIO.SampleEntity(
      appId == null ? null : appId.toString(),
      name == null ? null : name.toString(),
      type == null ? null : type.toString()
    );
  }
}
