package de.dlr.shepard.common.output;

import de.dlr.shepard.common.filters.RequestPathHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Map;

/**
 * JAX-RS filter that reads {@code ?profile=metadata|relations|all}
 * off the request and stamps the request-scoped
 * {@link OutputProfileResolver}. Only {@code /v2/...} paths are
 * processed — the upstream-frozen {@code /shepard/api/...} surface
 * ignores the parameter even when present.
 *
 * <p>Unknown profile name → RFC 7807 problem+json 400 listing the
 * valid names. Per {@code aidocs/56 §3}.
 */
@Provider
@RequestScoped
public class OutputProfileFilter implements ContainerRequestFilter {

  @Inject
  OutputProfileResolver resolver;

  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    String appPath = RequestPathHelper.applicationPath(request);
    // Only the /v2/... shelf honours the parameter.
    if (appPath == null || !appPath.startsWith("/v2/")) {
      return;
    }
    String raw = request.getUriInfo().getQueryParameters().getFirst("profile");
    if (raw == null || raw.isBlank()) {
      // Default already set by the resolver's @RequestScoped init.
      return;
    }
    OutputProfile parsed = OutputProfile.parse(raw);
    if (parsed == null) {
      Map<String, Object> body = Map.of(
        "type", "https://noheton.github.io/shepard/errors/validation.field",
        "title", "Invalid request",
        "status", 400,
        "detail", "Unknown ?profile= value. Valid values: " + OutputProfile.validNames() + ".",
        "field", "profile"
      );
      throw new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(body).build()
      );
    }
    resolver.setProfile(parsed);
  }
}
