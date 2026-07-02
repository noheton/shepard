package de.dlr.shepard.v2.semantic.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.context.semantic.daos.PredicateDAO;
import de.dlr.shepard.context.semantic.daos.VocabularyDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import de.dlr.shepard.context.semantic.entities.Vocabulary;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.semantic.io.PredicateIO;
import de.dlr.shepard.v2.vocabularies.io.VocabularyIO;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SEMA-V6-UI-FOLLOWUP — read-only browse surface for {@code :Vocabulary}
 * + {@code :Predicate} nodes seeded by V72 and the SEMA-V6 ontology
 * provider chain.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code GET /v2/semantic/vocabularies} — list all vocabularies
 *       (ordered by label ASC; includes both enabled and disabled rows so
 *       the operator can see what's hidden from autocomplete).</li>
 *   <li>{@code GET /v2/semantic/vocabularies/{vocabId}/predicates} — list
 *       the predicates declared by a given vocabulary, scoped by
 *       {@code :Predicate.vocabularyAppId = vocabId}.</li>
 * </ul>
 *
 * <p>This is the non-admin counterpart to
 * {@link de.dlr.shepard.v2.admin.semantic.SemanticAdminRest}: any
 * authenticated shepard user may browse the vocabulary catalogue, matching
 * the posture of {@link SemanticTermSearchRest} (no per-entity permission
 * check beyond authentication — the ontology catalogue is read-only and
 * visible to every logged-in user).
 *
 * <p>Design: {@code aidocs/semantics/100} §4 (Vocabularies + predicate model).
 * <p>Backlog row: {@code SEMA-V6-UI-FOLLOWUP}.
 */
@Path("/v2/semantic/vocabularies")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Semantics")
public class VocabularyBrowseRest {

  static final String PROBLEM_TYPE_NOT_FOUND = "/problems/semantic.vocabulary.not-found";

  @Inject
  VocabularyDAO vocabularyDAO;

  @Inject
  PredicateDAO predicateDAO;

  // ─── GET /v2/semantic/vocabularies ────────────────────────────────────────

  /**
   * {@code GET /v2/semantic/vocabularies}
   *
   * <p>Returns all {@code :Vocabulary} nodes, ordered by label ASC,
   * including disabled ones so the operator can see what's hidden from
   * autocomplete. Returns an empty list (200) when no vocabularies are
   * seeded.
   */
  @GET
  @Operation(
    operationId = "listVocabularies",
    summary = "List all vocabularies.",
    description =
      "Returns every :Vocabulary node seeded into the internal semantic store, " +
      "ordered by label ASC. Includes both enabled and disabled vocabularies — the " +
      "`enabled` flag tells callers which appear in autocomplete. " +
      "Auth: any authenticated user. " +
      "Empty list (200) when no vocabularies are seeded."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of vocabularies (may be empty).",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VocabularyIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listVocabularies() {
    List<Vocabulary> all = vocabularyDAO.listAll();
    List<VocabularyIO> out = all.stream().map(VocabularyIO::from).toList();
    return Response.ok(out).build();
  }

  // ─── GET /v2/semantic/vocabularies/{vocabId}/predicates ───────────────────

  /**
   * {@code GET /v2/semantic/vocabularies/{vocabId}/predicates}
   *
   * <p>Returns the predicates declared by the given vocabulary, scoped by
   * {@code :Predicate.vocabularyAppId = vocabId}. Returns 404 when no
   * vocabulary with that appId exists. Returns a 200 with an empty list
   * when the vocabulary exists but has no predicates yet.
   */
  @GET
  @Path("/{vocabId}/predicates")
  @Operation(
    operationId = "listPredicatesForVocabulary",
    summary = "List predicates declared by one vocabulary.",
    description =
      "Returns every :Predicate node whose `vocabularyAppId` equals the path " +
      "parameter, ordered by label ASC, paged via `?page=`/`?pageSize=` " +
      "(default page 0, pageSize 50, max 200). " +
      "Auth: any authenticated user. " +
      "Returns 404 when the vocabulary does not exist; returns 200 with " +
      "`items: []` when the vocabulary exists but has no predicates yet."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged predicates for this vocabulary (may be empty).",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PredicateIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No vocabulary with this appId.")
  public Response listPredicatesForVocabulary(
    @PathParam("vocabId") String vocabId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size (default 50, max 200).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    if (vocabId == null || vocabId.isBlank()) {
      return notFound(vocabId);
    }
    Vocabulary vocab = vocabularyDAO.findByAppId(vocabId);
    if (vocab == null) {
      return notFound(vocabId);
    }
    List<Predicate> rows = predicateDAO.listByVocabulary(vocabId);
    List<PredicateIO> all = rows.stream().map(PredicateIO::from).toList();
    int total = all.size();
    int from = (int) Math.min((long) page * pageSize, total);
    int to = (int) Math.min((long) from + pageSize, (long) total);
    return Response.ok(new PagedResponseIO<>(all.subList(from, to), total, page, pageSize)).build();
  }

  // ─── GET /v2/semantic/vocabularies/used-by/{entityAppId} ──────────────────

  /**
   * TOOLS-CONTEXT-VOCAB-BACKEND-1 — list the vocabularies whose terms are
   * referenced by at least one {@code :SemanticAnnotation} attached to the
   * given entity.
   *
   * <p>The {@code scope} query parameter selects the walk:
   * <ul>
   *   <li>{@code data-object} (default) — only the entity's own annotations.</li>
   *   <li>{@code collection} — also walks {@code [:HAS_DATAOBJECT*0..]}
   *       descendants so a Collection page shows every vocabulary used
   *       anywhere inside it.</li>
   * </ul>
   *
   * <p>Source: TOOLS-CONTEXT-VOCAB-BACKEND-1 (aidocs/16).
   */
  @GET
  @Path("/used-by/{entityAppId}")
  @Operation(
    operationId = "listVocabulariesUsedBy",
    summary = "List vocabularies referenced by an entity's annotations.",
    description =
      "Returns the subset of :Vocabulary nodes whose terms are referenced by at " +
      "least one :SemanticAnnotation on the given entity. When `scope=collection` " +
      "the walk includes descendants reachable via [:HAS_DATAOBJECT*0..]. " +
      "Auth: any authenticated user. Empty list (200) when no annotations match."
  )
  @APIResponse(
    responseCode = "200",
    description = "Vocabularies referenced by the entity (may be empty).",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = VocabularyIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listVocabulariesUsedBy(
    @PathParam("entityAppId") String entityAppId,
    @Parameter(
      description =
        "Annotation walk scope. 'data-object' (default): only the entity's own " +
        "annotations. 'collection': walks [:HAS_DATAOBJECT*0..] descendants too. " +
        "Any other value is treated as 'data-object'."
    )
    @QueryParam("scope") @DefaultValue("data-object") String scope
  ) {
    if (entityAppId == null || entityAppId.isBlank()) {
      return Response.ok(List.<VocabularyIO>of()).build();
    }
    List<Vocabulary> used = vocabularyDAO.findVocabulariesUsedByEntity(entityAppId, scope);
    List<VocabularyIO> out = used.stream().map(VocabularyIO::from).toList();
    return Response.ok(out).build();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static Response notFound(String vocabId) {
    ProblemJson body = new ProblemJson(
      PROBLEM_TYPE_NOT_FOUND,
      "Vocabulary not found",
      Status.NOT_FOUND.getStatusCode(),
      "No :Vocabulary node with appId='" + (vocabId == null ? "" : vocabId) + "'.",
      null
    );
    return Response.status(Status.NOT_FOUND)
      .type(MediaType.APPLICATION_JSON)
      .entity(body)
      .build();
  }
}
