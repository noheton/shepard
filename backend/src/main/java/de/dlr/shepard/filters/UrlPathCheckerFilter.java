package de.dlr.shepard.filters;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.exceptions.InvalidPathException;
import de.dlr.shepard.neo4Core.services.UrlPathChecker;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@RequestScoped
public class UrlPathCheckerFilter implements ContainerRequestFilter {

  @Inject
  UrlPathChecker urlPathChecker;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    try {
      urlPathChecker.checkPathSegments(requestContext.getUriInfo().getPathSegments());
    } catch (InvalidPathException e) {
      Log.warnf("Caught invalid path exception: %s", e.getMessage());
      var status = Status.NOT_FOUND.getStatusCode();
      requestContext.abortWith(
        Response.status(status).entity(new ApiError(status, e.getClass().toString(), e.getMessage())).build()
      );
    }
  }
}
