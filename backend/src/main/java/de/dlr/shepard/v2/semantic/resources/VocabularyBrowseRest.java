package de.dlr.shepard.v2.semantic.resources;

import de.dlr.shepard.v2.common.ProblemResponse;
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
 *   <li>{@code GET /v2/semantic/vocabularies/{appId}/predicates} — list
 *       the predicates declared by a given vocabulary, scoped by
 *       {@code :Predicate.vocabularyAppId = appId}.</li>
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
   *
   * <p>APISIMP-VOCAB-LIST-UNBOUNDED: {@code page} / {@code pageSize}
   * push SKIP/LIMIT to the database.
   */
  @GET
  @Operation(
    operationId = "listVocabularies",
    summary = "List all vocabularies.",
    description =
      "Returns :Vocabulary nodes seeded into the internal semantic store, " +
      "ordered by label ASC. Includes both enabled and disabled vocabularies — the " +
      "`enabled` flag tells callers which appear in autocomplete. " +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50). " +
      "Auth: any authenticated user. " +
      "Empty list (200) when no vocabularies are seeded."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listVocabularies(
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    long total = vocabularyDAO.count();
    List<Vocabulary> page_ = vocabularyDAO.listPaged((long) page * pageSize, pageSize);
    List<VocabularyIO> out = page_.stream().map(VocabularyIO::from).toList();
    return Response.ok(new PagedResponseIO<>(out, total, page, pageSize))
        .build();
  }

  // ─── GET /v2/semantic/vocabularies/{appId}/predicates ────────────────────

  /**
   * {@code GET /v2/semantic/vocabularies/{appId}/predicates}
   *
   * <p>Returns the predicates declared by the given vocabulary, scoped by
   * {@code :Predicate.vocabularyAppId = appId}. Returns 404 when no
   * vocabulary with that appId exists. Returns a 200 with an empty list
   * when the vocabulary exists but has no predicates yet.
   */
  @GET
  @Path("/{appId}/predicates")
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
    description = "Paged envelope: items + total + page + pageSize (APISIMP-PAGINATION-ENVELOPE).",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No vocabulary with this appId.")
  public Response listPredicatesForVocabulary(
    @PathParam("appId") String appId,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size (default 50, max 200).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    if (appId == null || appId.isBlank()) {
      return notFound(appId);
    }
    Vocabulary vocab = vocabularyDAO.findByAppId(appId);
    if (vocab == null) {
      return notFound(appId);
    }
    long total = predicateDAO.countByVocabulary(appId);
    long skip = Math.min((long) page * pageSize, total);
    List<Predicate> rows = predicateDAO.listByVocabularyPaged(appId, skip, pageSize);
    List<PredicateIO> page_ = rows.stream().map(PredicateIO::from).toList();
    return Response.ok(new PagedResponseIO<>(page_, total, page, pageSize))
        .build();
  }

  // ─── GET /v2/semantic/vocabularies/used-by/{appId} ───────────────────────

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
  @Path("/used-by/{appId}")
  @Operation(
    operationId = "listVocabulariesUsedBy",
    summary = "List vocabularies referenced by an entity's annotations.",
    description =
      "Returns the subset of :Vocabulary nodes whose terms are referenced by at " +
      "least one :SemanticAnnotation on the given entity. When `scope=collection` " +
      "the walk includes descendants reachable via [:HAS_DATAOBJECT*0..]. " +
      "Pagination: `page` (0-based, default 0) and `pageSize` (1–200, default 50). " +
      "Auth: any authenticated user. Empty list (200) when no annotations match."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged envelope: items + total + page + pageSize. Response body `total` carries the count.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listVocabulariesUsedBy(
    @PathParam("appId") String appId,
    @Parameter(
      description =
        "Annotation walk scope. 'data-object' (default): only the entity's own " +
        "annotations. 'collection': walks [:HAS_DATAOBJECT*0..] descendants too. " +
        "Any other value is treated as 'data-object'."
    )
    @QueryParam("scope") @DefaultValue("data-object") String scope,
    @Parameter(description = "Zero-based page index (default 0).")
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @Parameter(description = "Page size, 1–200 (default 50).")
    @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
  ) {
    if (appId == null || appId.isBlank()) {
      return Response.ok(new PagedResponseIO<>(List.<VocabularyIO>of(), 0L, page, pageSize))
          .build();
    }
    List<Vocabulary> used = vocabularyDAO.findVocabulariesUsedByEntity(appId, scope);
    long total = used.size();
    int skip = (int) Math.min((long) page * pageSize, total);
    List<Vocabulary> slice = used.subList(skip, (int) Math.min((long) skip + pageSize, total));
    List<VocabularyIO> out = slice.stream().map(VocabularyIO::from).toList();
    return Response.ok(new PagedResponseIO<>(out, total, page, pageSize))
        .build();
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static Response notFound(String id) {
    return ProblemResponse.problem(PROBLEM_TYPE_NOT_FOUND, "Vocabulary not found",
        Status.NOT_FOUND, "No :Vocabulary node with appId='" + (id == null ? "" : id) + "'.");
  }
}
