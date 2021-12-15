package de.dlr.shepard.filters;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PermissionType;
import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@Priority(Priorities.AUTHORIZATION)
public class PermissionsFilter implements ContainerRequestFilter {

	private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

	private static GracePeriodUtil lastSeen;

	public PermissionsFilter() {
		lastSeen = new GracePeriodUtil(FIVE_MINUTES_IN_MILLIS);
	}

	@Override
	public void filter(ContainerRequestContext requestContext) {

		var principal = requestContext.getSecurityContext().getUserPrincipal();
		if (principal == null || principal.getName() == null) {
			log.warn("Unknown principal {}", principal);
			abort(requestContext, "User could not be read from the request context");
			return;
		}

		var lastSeenKey = principal.getName() + requestContext.getMethod() + requestContext.getUriInfo().getPath();
		if (lastSeen.elementIsKnown(lastSeenKey)) {
			return;
		} else if (isAllowed(requestContext, principal.getName())) {
			lastSeen.elementSeen(lastSeenKey);
			return;
		}

		abort(requestContext, "The requested action is forbidden by the permission policies");

	}

	private boolean isAllowed(ContainerRequestContext requestContext, String username) {
		PermissionsService permissionsService = getPermissionsService();
		var managerPaths = List.of(Constants.PERMISSIONS);
		var writerMethods = List.of(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE);
		var readerMethods = List.of(HttpMethod.GET);

		var pathSegments = requestContext.getUriInfo().getPathSegments();
		var idSegment = pathSegments.size() > 1 ? pathSegments.get(1).getPath() : null;
		if (idSegment == null || idSegment.isBlank()) {
			// No id in path
			return true;
		} else if (!StringUtils.isNumeric(idSegment)) {
			// non-numeric id
			if (pathSegments.get(0).getPath().equals(Constants.USERS)) {
				if (pathSegments.size() <= 2 && requestContext.getMethod().equals(HttpMethod.GET))
					// it is allowed to read all users
					return true;
				else if (username.equals(idSegment))
					// it is allowed to manage yourself
					return true;
			}
			return false;
		}

		var entityId = Long.parseLong(idSegment);
		var perms = permissionsService.getPermissionsByEntity(entityId);
		if (perms == null)
			// No permissions
			return true;

		if (perms.getOwner() != null && username.equals(perms.getOwner().getUsername()))
			// Is owner
			return true;

		if (managerPaths.contains(pathSegments.get(pathSegments.size() - 1).getPath())) {
			if (perms.getManager().stream().anyMatch(u -> username.equals(u.getUsername())))
				// Is manager
				return true;
			return false;
		}

		if ((readerMethods.contains(requestContext.getMethod()) || writerMethods.contains(requestContext.getMethod()))
				&& PermissionType.Public.equals(perms.getPermissionType()))
			return true;

		if (readerMethods.contains(requestContext.getMethod())
				&& PermissionType.PublicReadable.equals(perms.getPermissionType()))
			return true;

		if (readerMethods.contains(requestContext.getMethod())) {
			if (perms.getReader().stream().anyMatch(u -> username.equals(u.getUsername())))
				// Is reader
				return true;
		} else if (writerMethods.contains(requestContext.getMethod())
				&& perms.getWriter().stream().anyMatch(u -> username.equals(u.getUsername())))
			// Is writer
			return true;

		return false;
	}

	private void abort(ContainerRequestContext requestContext, String reason) {
		requestContext.abortWith(Response.status(Status.FORBIDDEN)
				.entity(new ApiError(Status.FORBIDDEN.getStatusCode(), "AuthenticationException", reason)).build());
	}

	protected PermissionsService getPermissionsService() {
		return new PermissionsService();
	}

}
