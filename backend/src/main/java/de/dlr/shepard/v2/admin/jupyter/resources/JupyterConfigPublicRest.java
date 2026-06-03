package de.dlr.shepard.v2.admin.jupyter.resources;

import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
 * J1e — public read-only view of the {@code :JupyterConfig} singleton
 * for any authenticated user.
 *
 * <p>The unified data-references frontend table needs to know whether
 * to render the "Open in JupyterHub" action on {@code .ipynb} rows —
 * which means every authenticated user needs to discover the
 * {@code enabled} + {@code hubUrl} pair, but only an
 * {@code instance-admin} can mutate it. This resource exposes the
 * read view to non-admins; the generic
 * {@code GET|PATCH /v2/admin/config/jupyter} surface (V2CONV-A4) keeps
 * the full admin GET/PATCH for admins.
 *
 * <p>The two endpoints return byte-identical JSON shapes
 * ({@link JupyterConfigIO}). The split is purely an authorisation
 * concern: {@code /v2/admin/config/jupyter} requires the
 * {@code instance-admin} role; {@code /v2/jupyter/config} requires
 * only an authenticated principal.
 *
 * <p>No role gate at the class level — the JAX-RS principal check
 * happens at the method level via {@link SecurityContext}. This
 * matches the JWTFilter posture for other "any authenticated user can
 * read this" endpoints.
 */
@Path("/v2/jupyter/config")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Jupyter")
public class JupyterConfigPublicRest {

  @Inject
  JupyterConfigService service;

  @GET
  @Operation(
    summary = "Read the public JupyterHub link-out config (any authenticated user).",
    description =
      "Returns the same `{enabled, hubUrl}` shape as `GET /v2/admin/config/jupyter` " +
      "without the instance-admin role gate. Used by the unified data-references " +
      "frontend table to decide whether to render the 'Open in JupyterHub' action " +
      "on `.ipynb` rows.\n\n" +
      "Auth: any authenticated user (JWT or API key). Unauthenticated callers get 401."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current public-readable JupyterHub config.",
    content = @Content(schema = @Schema(implementation = JupyterConfigIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response getConfig(@Context SecurityContext sc) {
    if (sc.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    JupyterConfig cfg = service.current();
    return Response.ok(JupyterConfigIO.from(cfg, service.getDefaultHubUrl())).build();
  }
}
