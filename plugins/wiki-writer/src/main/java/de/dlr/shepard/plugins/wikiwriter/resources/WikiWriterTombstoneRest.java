package de.dlr.shepard.plugins.wikiwriter.resources;

import de.dlr.shepard.v2.common.ProblemResponse;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 410 Gone tombstone for the old per-collection wiki-write path.
 *
 * <p>The collection appId was redundant — the DataObject already belongs to
 * exactly one collection resolvable from dataObjectAppId. New path:
 * {@code POST /v2/data-objects/{dataObjectAppId}/wiki-write}.
 */
@Path("/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Wiki Writer")
public class WikiWriterTombstoneRest {

  @POST
  @Operation(
    operationId = "writeWikiJournalEntryLegacy",
    summary = "GONE — use POST /v2/data-objects/{dataObjectAppId}/wiki-write",
    description =
      "This path has been retired. The `{collectionAppId}` segment was redundant — " +
      "the DataObject's parent collection is resolved automatically. " +
      "Use `POST /v2/data-objects/{dataObjectAppId}/wiki-write` instead."
  )
  @APIResponse(responseCode = "410", description = "Gone — path retired; see Location header for replacement.")
  public Response wikiWrite(
    @PathParam("collectionAppId") String collectionAppId,
    @PathParam("dataObjectAppId") String dataObjectAppId
  ) {
    String newLocation = UriBuilder.fromPath("/v2/data-objects/{appId}/wiki-write")
      .build(dataObjectAppId)
      .toString();
    return ProblemResponse.problemBuilder(
        "urn:shepard:error:gone", "Gone", Response.Status.GONE.getStatusCode(),
        "This path has been retired. Use POST " + newLocation + " instead.")
      .header("Location", newLocation)
      .build();
  }
}
