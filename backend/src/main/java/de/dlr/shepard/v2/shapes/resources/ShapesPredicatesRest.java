package de.dlr.shepard.v2.shapes.resources;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.shapes.io.PredicateVocabularyEntryIO;
import de.dlr.shepard.v2.shapes.repositories.PredicateVocabularyRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;

/**
 * {@code GET /v2/shapes/predicates} — read-only view of the
 * {@code predicate_vocabulary} substrate-routing table.
 *
 * <p><b>What it does.</b> Returns the full predicate-vocabulary table
 * that maps {@code shepard:} predicate URIs to their authoritative
 * storage substrate (Neo4j, TimescaleDB, Postgres, Garage). Optionally
 * filtered by substrate via the {@code ?substrate=} query param.
 *
 * <p><b>Why it exists.</b> The SHACL shape write path (PR-5) uses this
 * table to route each predicate to the correct store. Exposing it as a
 * REST endpoint lets:
 * <ul>
 *   <li>Frontend shape editors discover which fields live where.</li>
 *   <li>MCP tools and external clients (Python, CLI) introspect the
 *       vocabulary without reading the migration SQL directly.</li>
 *   <li>Plugin authors verify that newly coined predicates have been
 *       registered before shipping a shape that uses them.</li>
 * </ul>
 *
 * <p><b>Access.</b> Any authenticated user — the vocabulary is
 * read-only metadata, not user data.
 *
 * <p><b>Status codes.</b>
 * <ul>
 *   <li>{@code 200} — vocabulary returned (may be empty if the table
 *       is unpopulated, e.g. on a fresh install before migrations).</li>
 *   <li>{@code 401} — not authenticated.</li>
 * </ul>
 *
 * <p><b>Cross-references.</b>
 * <ul>
 *   <li>{@code aidocs/semantics/98 §1.3} — predicate-key registry design</li>
 *   <li>{@code db/migration/V1.16.0__Add_predicate_vocabulary.sql} — seed data</li>
 *   <li>{@link ShapesValidateRest} — sibling validate endpoint</li>
 *   <li>{@link ShapesRenderRest} — sibling render endpoint</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/shapes")
@RequestScoped
@Tag(name = "Shapes")
public class ShapesPredicatesRest {

  private static final Set<String> ALLOWED_SUBSTRATES =
      Set.of("neo4j", "timescaledb", "postgres", "garage");

  @Inject
  PredicateVocabularyRepository repository;

  @GET
  @Path("/predicates")
  @RolesAllowed("authenticated")
  @Operation(
    operationId = "predicates",
    summary = "List the shepard: predicate vocabulary with substrate routing.",
    description = "Returns the predicate_vocabulary table entries mapping each known " +
    "shepard: predicate URI to its authoritative storage substrate " +
    "(neo4j | timescaledb | postgres | garage). " +
    "Optionally filtered by substrate. " +
    "Read-only — the table is populated by Flyway migrations."
  )
  @APIResponse(
    responseCode = "200",
    description = "Paged vocabulary entries, ordered by predicate_uri. Empty items list when no entries are registered.",
    content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
    headers = @Header(
      name = "X-Total-Count",
      description = "Total element count before paging.",
      schema = @Schema(type = SchemaType.INTEGER)
    )
  )
  @APIResponse(responseCode = "400", description = "Unknown substrate value.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response predicates(
    @Parameter(
      description = "Filter by substrate: neo4j | timescaledb | postgres | garage. " +
      "Omit to return the full vocabulary.",
      required = false
    )
    @QueryParam("substrate") String substrate,
    @Parameter(description = "Maximum entries per page (1–200). Default 200.", required = false)
    @QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(200) int pageSize,
    @Parameter(description = "Zero-based page index. Default 0.", required = false)
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page
  ) {
    long skip = (long) page * pageSize;
    long total;
    List<PredicateVocabularyEntryIO> items;
    if (substrate != null && !substrate.isBlank()) {
      // Re-derive from the trusted constant so CodeQL sees a clean (non-tainted) value
      // flowing to repository calls — Set.contains() alone does not break the taint chain.
      String cleanSub = ALLOWED_SUBSTRATES.stream()
          .filter(substrate.trim()::equals)
          .findFirst()
          .orElse(null);
      if (cleanSub == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Unknown substrate. Allowed values: neo4j, timescaledb, postgres, garage.")
            .build();
      }
      total = repository.countBySubstrate(cleanSub);
      items = skip >= total ? List.of() : repository.findBySubstrate(cleanSub, skip, pageSize);
    } else {
      total = repository.count();
      items = skip >= total ? List.of() : repository.findAll(skip, pageSize);
    }
    return Response.ok(new PagedResponseIO<>(items, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }
}
