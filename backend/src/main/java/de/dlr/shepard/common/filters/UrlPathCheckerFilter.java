package de.dlr.shepard.common.filters;

import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import io.micrometer.core.annotation.Timed;
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
  @Timed(value = "shepard.filters.url-path-checker", description = "Measure the duration of UrlPathCheckerFilter.")
  public void filter(ContainerRequestContext requestContext) throws IOException {
    try {
      urlPathChecker.assertIfIdsAreValid(
        requestContext.getUriInfo().getPathSegments(),
        requestContext.getUriInfo().getQueryParameters()
      );
    } catch (InvalidPathException e) {
      Log.warnf("Caught invalid path exception: %s", e.getMessage());
      var status = Status.NOT_FOUND.getStatusCode();
      requestContext.abortWith(
        Response.status(status).entity(new ApiError(status, e.getClass().toString(), e.getMessage())).build()
      );
    }
  }
}
