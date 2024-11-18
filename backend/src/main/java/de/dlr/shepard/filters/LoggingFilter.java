package de.dlr.shepard.filters;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@RequestScoped
public class LoggingFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    var method = requestContext.getMethod();
    var endpoint = requestContext.getUriInfo().getPath();
    var queryParams = requestContext.getUriInfo().getQueryParameters();
    if (requestContext.getSecurityContext().getUserPrincipal() == null) {
      Log.infof(
        "Received %s request without security context on %s with query params %s",
        method,
        endpoint,
        queryParams
      );
      return;
    }
    var username = requestContext.getSecurityContext().getUserPrincipal().getName();

    Log.infof("Received %s request on %s from %s with query params %s", method, endpoint, username, queryParams);
  }
}
