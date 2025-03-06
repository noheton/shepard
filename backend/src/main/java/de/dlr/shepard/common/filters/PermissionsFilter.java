package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.PermissionGracePeriod;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
public class PermissionsFilter implements ContainerRequestFilter {

  private static final List<String> writerMethods = List.of(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE);
  private static final List<String> readerMethods = List.of(HttpMethod.GET);

  private PermissionGracePeriod lastSeen;

  private PermissionsService permissionsService;

  PermissionsFilter() {}

  @Inject
  public PermissionsFilter(PermissionGracePeriod lastSeen, PermissionsService permissionsService) {
    this.lastSeen = lastSeen;
    this.permissionsService = permissionsService;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (PublicEndpointRegistry.isRequestPathPublic(requestContext)) return;
    var principal = requestContext.getSecurityContext().getUserPrincipal();
    if (principal == null || principal.getName() == null) {
      Log.warnf("Unknown principal %s", principal);
      abort(requestContext, "User could not be read from the request context");
      return;
    }

    var lastSeenKey = principal.getName() + requestContext.getMethod() + requestContext.getUriInfo().getPath();
    if (lastSeen.elementIsKnown(lastSeenKey)) {
      return;
    }
    var accessType = getAccessType(requestContext.getUriInfo().getPathSegments(), requestContext.getMethod());
    if (permissionsService.isAllowed(requestContext, accessType, principal.getName())) {
      lastSeen.elementSeen(lastSeenKey);
      return;
    }

    abort(requestContext, "The requested action is forbidden by the permission policies");
  }

  private void abort(ContainerRequestContext requestContext, String reason) {
    Log.warn(reason);
    requestContext.abortWith(
      Response.status(Status.FORBIDDEN)
        .entity(new ApiError(Status.FORBIDDEN.getStatusCode(), "AuthenticationException", reason))
        .build()
    );
  }

  private AccessType getAccessType(List<PathSegment> pathSegments, String requestMethod) {
    if (pathSegments.stream().anyMatch(seg -> seg.getPath().equals(Constants.PERMISSIONS))) return AccessType.Manage;
    if (readerMethods.contains(requestMethod)) return AccessType.Read;
    if (writerMethods.contains(requestMethod)) return AccessType.Write;
    return AccessType.None;
  }
}
