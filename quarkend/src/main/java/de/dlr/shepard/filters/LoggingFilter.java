package de.dlr.shepard.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class LoggingFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    var method = requestContext.getMethod();
    var username = requestContext.getSecurityContext().getUserPrincipal().getName();
    var endpoint = requestContext.getUriInfo().getPath();
    var queryParams = requestContext.getUriInfo().getQueryParameters();

    log.info("Received {} request on {} from {} with query params {}", method, endpoint, username, queryParams);
  }
}
