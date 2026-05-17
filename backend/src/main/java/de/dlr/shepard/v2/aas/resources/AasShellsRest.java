package de.dlr.shepard.v2.aas.resources;

import de.dlr.shepard.aas.services.AasShellMappingService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.v2.aas.io.AasShellIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AAS1a — {@code GET /v2/aas/shells}: minimal IDTA AAS v3 Shell listing.
 *
 * <p>Returns each Collection the caller may read as an {@link AasShellIO}
 * (one Shell per Collection). Permission filtering is delegated to
 * {@link CollectionDAO#findAllCollectionsByShepardId}, which gates on the
 * caller's username via the {@code HAS_PERMISSIONS} graph pattern.
 *
 * <p>Submodels are empty in AAS1a; AAS1b will populate them from DataObject
 * payloads. See {@code aidocs/52 §4a} for scope and roadmap.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/aas/shells")
@RequestScoped
@Tag(name = "AAS")
public class AasShellsRest {

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  AasShellMappingService mappingService;

  @Inject
  AuthenticationContext authenticationContext;

  @GET
  @Operation(
      summary = "List Collections as IDTA AAS v3 Shells (AAS1a).",
      description = "Returns one AssetAdministrationShell per Collection the authenticated caller " +
          "may read. Shells carry `id` (URN from appId), `idShort` (sanitised name), " +
          "`assetInformation`, optional `description`, and an empty `submodels` list. " +
          "Submodels are populated by AAS1b. See `aidocs/52 §4a`.")
  @APIResponse(
      responseCode = "200",
      description = "List of Shells readable by the caller. Empty list when the caller has no " +
          "readable Collections.",
      content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = AasShellIO.class)))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listShells(
      @Parameter(description = "Zero-based page number. Requires `size`.")
      @QueryParam("page") Integer page,
      @Parameter(description = "Page size.")
      @QueryParam("size") @DefaultValue("100") Integer size) {

    String username = authenticationContext.getCurrentUserName();

    var params = new QueryParamHelper();
    if (page != null) {
      params = params.withPageAndSize(page, size);
    }

    List<AasShellIO> shells = collectionDAO
        .findAllCollectionsByShepardId(params, username)
        .stream()
        .map(mappingService::toShell)
        .toList();

    return Response.ok(shells).build();
  }
}
