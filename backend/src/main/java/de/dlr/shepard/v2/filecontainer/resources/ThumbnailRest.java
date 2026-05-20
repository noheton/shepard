package de.dlr.shepard.v2.filecontainer.resources;

import de.dlr.shepard.data.file.thumbnail.ThumbnailService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * TH1a — thumbnail endpoint for FileContainer payloads.
 *
 * <p>Route: {@code GET /v2/file-containers/{containerAppId}/payload/{oid}/thumbnail?size=200}
 *
 * <p>Accepted sizes: 64, 200, 400 (longest side in pixels). Any other value
 * is normalised to 400.  Returns {@code image/png}.
 *
 * <p>HTTP 404 when the file type is not supported by any installed
 * {@link de.dlr.shepard.data.file.thumbnail.ThumbnailProvider}; the frontend
 * should fall back to a file-type icon in that case.
 */
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — thumbnails (TH1a)")
public class ThumbnailRest {

  private static final int DEFAULT_SIZE = 400;
  private static final int[] VALID_SIZES = {64, 200, 400};

  @Inject
  ThumbnailService thumbnailService;

  @GET
  @Path("/{containerAppId}/payload/{oid}/thumbnail")
  @Produces("image/png")
  @Operation(
    summary = "Get a PNG thumbnail for a file payload.",
    description = "Returns a PNG-encoded thumbnail scaled to at most `size` pixels on the longest side. " +
    "Valid sizes: 64, 200, 400. Any other value is treated as 400. " +
    "Returns 404 when the file type is not supported by any installed ThumbnailProvider. " +
    "Cache-Control: public, max-age=3600."
  )
  @APIResponse(
    responseCode = "200",
    description = "PNG thumbnail.",
    content = @Content(schema = @Schema(type = SchemaType.STRING, format = "binary"))
  )
  @APIResponse(responseCode = "400", description = "Invalid size parameter.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "Container, file, or thumbnail not available (unsupported type).")
  @APIResponse(responseCode = "503", description = "Thumbnail generation temporarily unavailable (timeout or queue full).")
  public Response getThumbnail(
    @PathParam("containerAppId") String containerAppId,
    @PathParam("oid") String oid,
    @QueryParam("size") Integer sizeParam
  ) {
    int sizePx = normaliseSize(sizeParam);

    byte[] pngBytes;
    try {
      pngBytes = thumbnailService.getThumbnail(containerAppId, oid, sizePx);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    } catch (ServiceUnavailableException sue) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .header("Retry-After", "5")
        .entity(sue.getMessage())
        .build();
    }

    if (pngBytes == null) {
      throw new NotFoundException("No thumbnail available for this file type.");
    }

    CacheControl cc = new CacheControl();
    cc.setMaxAge(3600);
    return Response.ok(pngBytes, "image/png")
      .cacheControl(cc)
      .build();
  }

  private static int normaliseSize(Integer sizeParam) {
    if (sizeParam == null) return DEFAULT_SIZE;
    for (int v : VALID_SIZES) {
      if (sizeParam == v) return v;
    }
    return DEFAULT_SIZE;
  }
}
