package de.dlr.shepard.plugins.jupyter.resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * J1e-PLUGIN-REFACTOR (2026-05-29) — compat shim for the legacy
 * public read path {@code /v2/jupyter/config}. The canonical path
 * is {@code /v2/plugins/jupyter/config}.
 *
 * <p>Same shape as {@link JupyterConfigLegacyRest}: delegates to the
 * canonical resource and stamps {@code Deprecation: true} + a
 * {@code Link} header pointing at the canonical path. Scheduled for
 * removal after one deprecation window — tracked in
 * {@code aidocs/34} (J1e-PR-07).
 */
@Path("/v2/jupyter/config")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "Jupyter")
public class JupyterConfigLegacyPublicRest {

  static final String DEPRECATION_HEADER = "Deprecation";
  static final String LINK_HEADER_VALUE = "</v2/plugins/jupyter/config>; rel=\"successor-version\"";

  @Inject
  JupyterConfigPublicRest canonical;

  @GET
  @Operation(
    summary = "[DEPRECATED] Read the public JupyterHub link-out config.",
    description = "Deprecated path. Use GET /v2/plugins/jupyter/config. Response includes the " +
    "Deprecation + Link headers per draft-ietf-httpapi-deprecation-header."
  )
  public Response getConfig(@Context SecurityContext sc) {
    Response upstream = canonical.getConfig(sc);
    return Response.fromResponse(upstream)
      .header(DEPRECATION_HEADER, "true")
      .header(HttpHeaders.LINK, LINK_HEADER_VALUE)
      .build();
  }
}
