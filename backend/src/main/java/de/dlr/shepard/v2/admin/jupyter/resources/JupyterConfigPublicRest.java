package de.dlr.shepard.v2.admin.jupyter.resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Tombstone — {@code GET /v2/jupyter/config} has permanently moved to the
 * generic public-read surface: {@code GET /v2/config/jupyter}.
 *
 * <p>Returns 301 Moved Permanently so existing callers automatically follow
 * the redirect. The canonical implementation now lives in
 * {@link de.dlr.shepard.v2.config.resources.PublicConfigRest}; the
 * {@code JupyterConfigDescriptor} declares {@code publicRead() = true}.
 *
 * <p>APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC.
 */
@Path("/v2/jupyter/config")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Jupyter")
public class JupyterConfigPublicRest {

  /** Canonical replacement path. */
  static final URI CANONICAL = URI.create("/v2/config/jupyter");

  @GET
  @Operation(
    operationId = "getJupyterConfig",
    deprecated = true,
    summary = "[MOVED] Use GET /v2/config/jupyter instead.",
    description =
      "This path has permanently moved to `GET /v2/config/jupyter`. Returns " +
      "301 Moved Permanently with a `Location` header pointing to the canonical URL."
  )
  @APIResponse(responseCode = "301", description = "Permanently moved to /v2/config/jupyter.")
  public Response getConfig() {
    return Response.status(Response.Status.MOVED_PERMANENTLY)
      .location(CANONICAL)
      .build();
  }
}
