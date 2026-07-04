package de.dlr.shepard.v2.semantic.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.OntologyAlignmentDAO;
import de.dlr.shepard.context.semantic.entities.OntologyAlignment;
import de.dlr.shepard.v2.semantic.io.OntologyAlignmentIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TPL3a-lite — read-only endpoint exposing the upper-ontology alignment
 * registry seeded by {@code V67__TPL3_upper_ontology_alignment.cypher}.
 *
 * <p>Endpoint: {@code GET /v2/semantic/ontology/alignment}
 *
 * <p>Returns the full set of {@link OntologyAlignment} rows that record how
 * each core Shepard concept (Collection, DataObject, SemanticAnnotation, …)
 * maps onto an upper-ontology class (BFO 2020, IAO, PROV-O, IOF Core).
 * The mapping authority is
 * {@code aidocs/semantics/96-upper-ontology-alignment.md}.
 *
 * <p><b>Auth.</b> Requires the {@code instance-admin} role.  The alignment
 * registry is an administrative concept — it describes the platform's own
 * semantic positioning and is not intended for end-user consumption.  There
 * is no write endpoint; mutations must go through Cypher migrations.
 *
 * <p><b>Stability.</b> The set is small (≤ ~50 rows) and changes only when
 * a new migration is applied, so no pagination is applied.  Callers may
 * cache the response freely; there is no ETag or Last-Modified header in
 * v1 of this endpoint.
 *
 * @see OntologyAlignmentIO
 * @see de.dlr.shepard.context.semantic.daos.OntologyAlignmentDAO
 */
@Path("/v2/semantic/ontology/alignment")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Semantics")
public class OntologyAlignmentRest {

  @Inject
  OntologyAlignmentDAO ontologyAlignmentDAO;

  /**
   * {@code GET /v2/semantic/ontology/alignment}
   *
   * <p>Returns all alignment rows.  Returns an empty array when V67 has not
   * yet been applied (fresh database).
   *
   * @return 200 with a JSON array of {@link OntologyAlignmentIO} objects.
   *         401 when the caller is unauthenticated.
   *         403 when the caller is authenticated but lacks the
   *         {@code instance-admin} role.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
    operationId = "listOntologyAlignments",
    summary = "List all upper-ontology alignment rows.",
    description =
      "Returns the complete set of `(:OntologyAlignment)` nodes seeded by " +
      "`V67__TPL3_upper_ontology_alignment.cypher`. Each row records how a " +
      "core Shepard concept maps onto an upper-ontology class (BFO 2020, IAO, " +
      "PROV-O, IOF Core), including the OWL relationship type, confidence " +
      "level, and aidocs source reference.\n\n" +
      "The registry is read-only at runtime; mutations require a Cypher " +
      "migration. Returns an empty array when the migration has not yet run.\n\n" +
      "Auth: requires `instance-admin` role."
  )
  @APIResponse(
    responseCode = "200",
    description =
      "Array of alignment rows (may be empty if V67 migration has not run yet).",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON,
      schema = @Schema(type = SchemaType.ARRAY, implementation = OntologyAlignmentIO.class)
    )
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "instance-admin role required.")
  public Response list() {
    List<OntologyAlignment> entities = ontologyAlignmentDAO.findAll();
    List<OntologyAlignmentIO> body = entities.stream()
      .map(OntologyAlignmentIO::from)
      .toList();
    return Response.ok(body).build();
  }
}
