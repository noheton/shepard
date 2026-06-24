package de.dlr.shepard.v2.dataobject.resources;

import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-DO-ROOT-PATH-INCONSISTENCY — collection-scoped alias for
 * {@link DataObjectRdfRest}.
 *
 * <p>Adds {@code GET /v2/collections/{collectionAppId}/data-objects/{appId}/rdf}
 * so callers already navigating the collection-scoped CRUD surface
 * ({@code /v2/collections/{cId}/data-objects}) do not need to switch to
 * the flat root ({@code /v2/data-objects/{appId}/rdf}) just to reach the
 * RDF subgraph endpoint.
 *
 * <p>The {@code collectionAppId} segment is accepted for URL consistency but
 * is not independently validated against the DataObject's actual parent —
 * the flat-path delegate resolves the parent Collection server-side and
 * enforces Read permission there. Response body is byte-identical to the
 * flat-path endpoint.
 */
@Path("/v2/collections/{collectionAppId}/data-objects")
@RequestScoped
@Authenticated
@Tag(name = "DataObjects")
public class DataObjectCollectionScopedRdfRest {

  @Inject
  DataObjectRdfRest delegate;

  @GET
  @Path("/{appId}/rdf")
  @Produces("text/turtle")
  @Operation(
    summary = "Return a Turtle subgraph for the DataObject (collection-scoped alias).",
    description =
      "Collection-scoped alias for `GET /v2/data-objects/{appId}/rdf`. " +
      "The `collectionAppId` path segment is accepted for URL consistency with the " +
      "collection-scoped CRUD surface but is not independently validated against the " +
      "DataObject's actual parent — the delegate resolves the parent Collection " +
      "server-side and enforces Read permission there. " +
      "Response body is byte-identical to the flat-path endpoint.\n\n" +
      "Auth: Read on the parent Collection. Returns 404 when no such " +
      "DataObject exists, 403 when the caller lacks Read."
  )
  @APIResponse(responseCode = "200", description = "Turtle document.", content = @Content(mediaType = "text/turtle"))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the parent Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId.")
  public Response getRdf(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("appId") @NotBlank String appId,
    @Context SecurityContext sc
  ) {
    return delegate.getRdf(appId, sc);
  }
}
