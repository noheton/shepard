package de.dlr.shepard.v2.thermography.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.v2.thermography.io.AnalyzeRequestIO;
import de.dlr.shepard.v2.thermography.io.AnalyzeResultIO;
import de.dlr.shepard.v2.thermography.io.PlateHeatmapIO;
import de.dlr.shepard.v2.thermography.services.ThermographyAnalysisService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * MFFD-NDT-QUALITY-1 — thermography quality-score + plate-heatmap REST.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /v2/thermography/analyze} — run a fresh analysis pass
 *       over a {@link FileBundleReference}'s TIFF frames; persist derived
 *       {@code urn:shepard:ndt:*} annotations; return a summary.</li>
 *   <li>{@code GET  /v2/thermography/{imageBundleAppId}/plate-heatmap} —
 *       return the cached composite plate-heatmap grid for the frontend
 *       Canvas renderer. 404 when the bundle has not been analyzed.</li>
 * </ul>
 *
 * <p>Permissions follow the standard reference-rooted policy: Read on the
 * parent DataObject to fetch the heatmap; Write on the parent DataObject to
 * run the analysis (because the analysis writes :SemanticAnnotation rows).
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/thermography")
@RequestScoped
@Tag(name = "Thermography (v2)",
  description = "MFFD-NDT-QUALITY-1 — per-frame peak-delta-c stats + plate "
    + "heatmap for thermography ImageBundle references. Pure deterministic "
    + "metric computation; no AI backend required (local-default rule).")
public class ThermographyV2Rest {

  @Inject
  ThermographyAnalysisService thermographyAnalysisService;

  @Inject
  FileBundleReferenceDAO fileBundleReferenceDAO;

  @Inject
  PermissionsService permissionsService;

  // ── helpers ────────────────────────────────────────────────────────────

  private Response checkAccess(String imageBundleAppId, AccessType type, SecurityContext sc) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    FileBundleReference bundle = fileBundleReferenceDAO.findByAppId(imageBundleAppId);
    if (bundle == null) return Response.status(Response.Status.NOT_FOUND).build();
    var parent = bundle.getDataObject();
    if (parent == null) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessAllowedForDataObjectAppId(parent.getAppId(), type, caller)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return null;
  }

  // ── endpoints ──────────────────────────────────────────────────────────

  @POST
  @Path("/analyze")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
    summary = "Run thermography analysis on a FileBundleReference of TIFFs.",
    description =
      "Streams every TIFF in the bundle through the TwelveMonkeys ImageIO "
        + "reader, computes per-frame `peak_delta_c` + `mean_delta_c` + "
        + "hot-spot pixel, and builds a composite plate-heatmap. Writes "
        + "the bundle-level summary as `:SemanticAnnotation` rows under "
        + "`urn:shepard:ndt:*` (sourceMode=ai, confidence=1.0); writes a "
        + "DataObject-level `urn:shepard:ndt:quality-score` derived from "
        + "`max(peak_delta_c)/threshold_c` (max-across-bundles policy).\n\n"
        + "Idempotent — re-running the analysis wipes the previous "
        + "`urn:shepard:ndt:*` rows on the bundle before re-writing.\n\n"
        + "Threshold defaults to `shepard.v2.thermography.threshold-c` "
        + "(deploy default 80°C); per-call override via the request body."
  )
  @APIResponse(
    responseCode = "200",
    description = "Analysis complete; `AnalyzeResultIO` summarises the "
      + "computed metrics and the number of annotations written.",
    content = @Content(schema = @Schema(implementation = AnalyzeResultIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing imageBundleAppId or bundle not found.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileBundleReference with the supplied appId.")
  public Response analyze(AnalyzeRequestIO body, @Context SecurityContext sc) {
    if (body == null || body.getImageBundleAppId() == null || body.getImageBundleAppId().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("imageBundleAppId is required").build();
    }
    Response gate = checkAccess(body.getImageBundleAppId(), AccessType.Write, sc);
    if (gate != null) return gate;
    try {
      AnalyzeResultIO result = thermographyAnalysisService.analyze(
        body.getImageBundleAppId(),
        body.getThresholdC(),
        body.getGridWidth(),
        body.getGridHeight()
      );
      return Response.ok(result).build();
    } catch (InvalidBodyException ex) {
      return Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build();
    }
  }

  @GET
  @Path("/{imageBundleAppId}/plate-heatmap")
  @Operation(
    summary = "Return the cached plate-heatmap grid for a thermography bundle.",
    description =
      "Returns the composite max-temperature grid built at analyze time, "
        + "row-major as `cells[height][width]` floats (degrees Celsius). "
        + "404 when the bundle has not been analyzed — the UI surfaces the "
        + "Re-analyze button in that state."
  )
  @APIResponse(
    responseCode = "200",
    description = "Heatmap payload.",
    content = @Content(schema = @Schema(implementation = PlateHeatmapIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "Bundle does not exist or has not been analyzed.")
  public Response plateHeatmap(@PathParam("imageBundleAppId") String imageBundleAppId,
                               @Context SecurityContext sc) {
    Response gate = checkAccess(imageBundleAppId, AccessType.Read, sc);
    if (gate != null) return gate;
    PlateHeatmapIO heatmap = thermographyAnalysisService.readPlateHeatmap(imageBundleAppId);
    if (heatmap == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("No cached plate-heatmap for this bundle. Run POST /v2/thermography/analyze first.")
        .build();
    }
    return Response.ok(heatmap).build();
  }
}
