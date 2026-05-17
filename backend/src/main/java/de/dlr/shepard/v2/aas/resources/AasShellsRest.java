package de.dlr.shepard.v2.aas.resources;

import de.dlr.shepard.aas.services.AasShellMappingService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.aas.io.AasShellIO;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * AAS1a/AAS1b — {@code GET /v2/aas/shells}: IDTA AAS v3 Shell listing and single-Shell lookup.
 *
 * <p>AAS1a: list endpoint returns each readable Collection as a Shell.
 * AAS1b Commit 1: {@code GET /v2/aas/shells/{aasId}} resolves a single Shell by
 * raw appId or base64url-encoded IRI per IDTA-01002-3-2 §4.3.
 *
 * <p>Permission discipline: returns 404 (not 403) when a Shell does not exist or the
 * caller lacks read access — matches shepard's 404-on-no-read contract (aidocs/52 §7).
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

  @GET
  @Path("/{aasId}")
  @Operation(
      summary = "Get a single Collection as an IDTA AAS v3 Shell (AAS1b).",
      description = "Accepts `{aasId}` as (a) the base64url-encoded Shell IRI " +
          "(`urn:shepard:collection:{appId}`) per IDTA-01002-3-2 §4.3, or " +
          "(b) the bare shepard Collection appId. " +
          "Returns 404 when the Shell does not exist or the caller lacks read access " +
          "(404-on-no-read discipline — not 403). See `aidocs/integrations/52-aas-backend-integration.md §7`.")
  @APIResponse(
      responseCode = "200",
      description = "Shell for the requested Collection.",
      content = @Content(schema = @Schema(implementation = AasShellIO.class)))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Shell not found or caller lacks read access.")
  public Response getShell(
      @Parameter(description = "Base64url-encoded Shell IRI per IDTA-01002-3-2 §4.3, " +
          "or bare Collection appId.")
      @PathParam("aasId") String aasId) {

    String username = authenticationContext.getCurrentUserName();
    String appId = resolveAppId(aasId);
    Collection collection = collectionDAO.findByAppId(appId, username);
    if (collection == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(mappingService.toShell(collection)).build();
  }

  /**
   * Resolves an IDTA AAS {@code aasIdentifier} to a shepard Collection appId.
   *
   * <p>IDTA-01002-3-2 §4.3: path params carry the base64url-encoded form of the
   * Shell {@code id} IRI. Shepard also accepts the bare appId directly.
   *
   * <p>Resolution: try base64url-decode; if the result starts with the Collection
   * URN prefix strip it and return the trailing appId. Otherwise return the raw
   * input (bare appId path).
   */
  static String resolveAppId(String aasId) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(aasId), StandardCharsets.UTF_8);
      if (decoded.startsWith(AasShellMappingService.COLLECTION_URN_PREFIX)) {
        return decoded.substring(AasShellMappingService.COLLECTION_URN_PREFIX.length());
      }
    } catch (IllegalArgumentException ignored) {
      // not base64url — treat as raw appId
    }
    return aasId;
  }
}
