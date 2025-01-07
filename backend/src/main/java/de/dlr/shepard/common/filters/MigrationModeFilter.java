package de.dlr.shepard.common.filters;

import de.dlr.shepard.common.configuration.feature.toggles.MigrationModeToggle;
import de.dlr.shepard.common.exceptions.ApiError;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@RequestScoped
@Priority(999)
public class MigrationModeFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (!MigrationModeToggle.isActive()) return;
    if (PublicEndpointRegistry.isRequestPathPublic(requestContext)) return;
    if (requestContext.getUriInfo().getPath().startsWith("/temp")) return;

    requestContext.abortWith(
      Response.status(Status.SERVICE_UNAVAILABLE)
        .entity(
          new ApiError(
            Status.SERVICE_UNAVAILABLE.getStatusCode(),
            "ServiceUnavailableException",
            "The application is in migration mode right now. " +
            "You can retrieve the current migration status at <backend-url>/shepard/api/temp/migrations/state"
          )
        )
        .build()
    );
  }
}
