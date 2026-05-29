package de.dlr.shepard.plugins.jupyter.resources;

import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import de.dlr.shepard.plugins.jupyter.io.JupyterConfigPatchIO;

/**
 * J1e-PLUGIN-REFACTOR (2026-05-29) — compat shim for the legacy
 * in-tree admin path {@code /v2/admin/jupyter/config}. Pre-2026-05-29
 * clients (CLI v &lt; the relocated build, hand-rolled scripts) still
 * call this path; the canonical surface is
 * {@code /v2/admin/plugins/jupyter/config} per the plugin-routing
 * convention.
 *
 * <p>Implementation: thin delegation to {@link JupyterConfigRest},
 * with a {@code Deprecation: true} response header per
 * <a href="https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-deprecation-header">draft-ietf-httpapi-deprecation-header</a>
 * and a {@code Link} header pointing at the canonical path. The shim
 * itself is scheduled for removal after one deprecation window —
 * tracked in {@code aidocs/34} (J1e-PR-07).
 */
@Path("/v2/admin/jupyter/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class JupyterConfigLegacyRest {

  static final String DEPRECATION_HEADER = "Deprecation";
  static final String LINK_HEADER_VALUE = "</v2/admin/plugins/jupyter/config>; rel=\"successor-version\"";

  @Inject
  JupyterConfigRest canonical;

  @GET
  @Operation(
    summary = "[DEPRECATED] Read the :JupyterConfig singleton.",
    description = "Deprecated path. Use GET /v2/admin/plugins/jupyter/config. Response includes the " +
    "Deprecation + Link headers per draft-ietf-httpapi-deprecation-header."
  )
  public Response getConfig() {
    return withDeprecationHeaders(canonical.getConfig());
  }

  @PATCH
  @Consumes({ "application/merge-patch+json", MediaType.APPLICATION_JSON })
  @Operation(
    summary = "[DEPRECATED] Merge-patch the :JupyterConfig singleton.",
    description = "Deprecated path. Use PATCH /v2/admin/plugins/jupyter/config. Response includes the " +
    "Deprecation + Link headers per draft-ietf-httpapi-deprecation-header."
  )
  public Response patchConfig(JupyterConfigPatchIO body) {
    return withDeprecationHeaders(canonical.patchConfig(body));
  }

  private static Response withDeprecationHeaders(Response upstream) {
    return Response.fromResponse(upstream)
      .header(DEPRECATION_HEADER, "true")
      .header(HttpHeaders.LINK, LINK_HEADER_VALUE)
      .build();
  }
}
