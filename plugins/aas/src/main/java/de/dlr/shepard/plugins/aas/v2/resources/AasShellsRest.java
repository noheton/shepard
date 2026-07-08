package de.dlr.shepard.plugins.aas.v2.resources;

import de.dlr.shepard.plugins.aas.services.AasConfigService;
import de.dlr.shepard.plugins.aas.services.AasShellMappingService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.plugins.aas.v2.io.AasReferenceIO;
import de.dlr.shepard.plugins.aas.v2.io.AasShellIO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

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

  static final int SHELL_MAX_SUBMODELS = 500;

  @Inject
  AasConfigService aasConfigService;

  @Inject
  CollectionDAO collectionDAO;

  @Inject
  DataObjectDAO dataObjectDAO;

  @Inject
  AasShellMappingService mappingService;

  @Inject
  AuthenticationContext authenticationContext;

  private boolean aasDisabled() {
    return !aasConfigService.current().isEnabled();
  }

  private static Response aasDisabledResponse() {
    return problem("/problems/aas.integration-disabled", "AAS Integration Disabled", 501,
        "AAS integration is disabled on this instance. Enable via PATCH /v2/admin/aas/config.");
  }

  @GET
  @Operation(
      operationId = "listAasShells",
      summary = "List Collections as IDTA AAS v3 Shells (AAS1a).",
      description = "Returns one AssetAdministrationShell per Collection the authenticated caller " +
          "may read. Shells carry `id` (URN from appId), `idShort` (sanitised name), " +
          "`assetInformation`, optional `description`, and an empty `submodels` list. " +
          "Submodels are populated by AAS1b. See `aidocs/integrations/52-aas-backend-integration.md §4a`. " +
          "Uses page-offset pagination (default page=0, pageSize=50, server cap 200).")
  @APIResponse(
      responseCode = "200",
      description = "Paged list of Shells readable by the caller. " +
          "Envelope: { items[], total, page, pageSize }.",
      content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
      headers = @Header(
          name = "X-Total-Count",
          description = "Total element count before paging.",
          schema = @Schema(type = SchemaType.INTEGER)
      ))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response listShells(
      @Parameter(description = "Zero-based page number (default 0). Returns 400 when negative.")
      @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
      @Parameter(description = "Page size, range [1, 200]. Default 50. Returns 400 when out of range.")
      @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize) {

    if (aasDisabled()) return aasDisabledResponse();

    String username = authenticationContext.getCurrentUserName();
    var params = new QueryParamHelper().withPageAndSize(page, pageSize);
    long total = collectionDAO.countAllCollectionsByShepardId(username);

    List<AasShellIO> shells = collectionDAO
        .findAllCollectionsByShepardId(params, username)
        .stream()
        .map(mappingService::toShell)
        .toList();

    return Response.ok(new PagedResponseIO<>(shells, total, page, pageSize))
        .header("X-Total-Count", total)
        .build();
  }

  @GET
  @Path("/{aasId}")
  @Operation(
      operationId = "getAasShell",
      summary = "Get a single Collection as an IDTA AAS v3 Shell (AAS1b).",
      description = "Accepts `{aasId}` as (a) the base64url-encoded Shell IRI " +
          "(`urn:shepard:collection:{appId}`) per IDTA-01002-3-2 §4.3, or " +
          "(b) the bare shepard Collection appId. " +
          "Returns 404 when the Shell does not exist or the caller lacks read access " +
          "(404-on-no-read discipline — not 403). " +
          "Inline submodel references are capped at " + SHELL_MAX_SUBMODELS + "; " +
          "when the Collection has more top-level DataObjects the response carries " +
          "`X-Shepard-Truncated: true` and `X-Shepard-Truncated-At: " + SHELL_MAX_SUBMODELS + "`. " +
          "See `aidocs/integrations/52-aas-backend-integration.md §7`.")
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

    if (aasDisabled()) return aasDisabledResponse();

    String username = authenticationContext.getCurrentUserName();
    String appId = resolveAppId(aasId);
    Collection collection = collectionDAO.findByAppId(appId, username);
    if (collection == null) {
      return problem(Response.Status.NOT_FOUND, "AAS Shell not found");
    }
    // Load at most SHELL_MAX_SUBMODELS+1 rows from the DB so the JVM heap
    // is bounded regardless of Collection size (APISIMP-AAS-SHELL-DO-LOAD-CAP).
    List<DataObject> probe = dataObjectDAO.findTopLevelByCollectionAppId(
        appId, 0, SHELL_MAX_SUBMODELS + 1);
    boolean truncated = probe.size() > SHELL_MAX_SUBMODELS;
    List<DataObject> capped = truncated ? probe.subList(0, SHELL_MAX_SUBMODELS) : probe;
    Response.ResponseBuilder rb = Response.ok(mappingService.toShell(collection, capped));
    if (truncated) {
      rb.header("X-Shepard-Truncated", "true")
        .header("X-Shepard-Truncated-At", SHELL_MAX_SUBMODELS);
    }
    return rb.build();
  }

  @GET
  @Path("/{aasId}/submodels")
  @Operation(
      operationId = "listAasShellSubmodels",
      summary = "List Submodel references for a Shell (AAS1b).",
      description = "Returns one IDTA AAS v3 Reference per top-level DataObject of the Collection " +
          "identified by `{aasId}` (base64url-encoded Shell IRI or bare appId). " +
          "Each Reference has `type=ExternalReference` and a single Submodel key with value " +
          "`urn:shepard:dataobject:{appId}`. Returns 404 when the Shell does not exist or " +
          "the caller lacks read access (404-on-no-read discipline). " +
          "See `aidocs/integrations/52-aas-backend-integration.md §4b`. " +
          "Uses page-offset pagination (default page=0, pageSize=50, server cap 200).")
  @APIResponse(
      responseCode = "200",
      description = "Paged Submodel references for the requested Shell. " +
          "Envelope: { items[], total, page, pageSize }.",
      content = @Content(schema = @Schema(implementation = PagedResponseIO.class)),
      headers = @Header(
          name = "X-Total-Count",
          description = "Total element count before paging.",
          schema = @Schema(type = SchemaType.INTEGER)
      ))
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Shell not found or caller lacks read access.")
  public Response listSubmodels(
      @Parameter(description = "Base64url-encoded Shell IRI per IDTA-01002-3-2 §4.3, " +
          "or bare Collection appId.")
      @PathParam("aasId") String aasId,
      @Parameter(description = "Zero-based page number (default 0). Returns 400 when negative.")
      @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
      @Parameter(description = "Page size, range [1, 200]. Default 50. Returns 400 when out of range.")
      @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize) {

    if (aasDisabled()) return aasDisabledResponse();

    String username = authenticationContext.getCurrentUserName();
    String appId = resolveAppId(aasId);
    Collection collection = collectionDAO.findByAppId(appId, username);
    if (collection == null) {
      return problem(Response.Status.NOT_FOUND, "AAS Shell not found");
    }
    long total = dataObjectDAO.countTopLevelByCollectionAppId(appId);
    List<DataObject> dataObjects = dataObjectDAO.findTopLevelByCollectionAppId(appId, page, pageSize);
    return Response.ok(
        new PagedResponseIO<>(mappingService.toSubmodelRefs(dataObjects), total, page, pageSize)
    ).header("X-Total-Count", total).build();
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
