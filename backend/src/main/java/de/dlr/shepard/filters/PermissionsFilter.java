package de.dlr.shepard.filters;

import java.util.List;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.Constants;
import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@Priority(Priorities.AUTHORIZATION)
public class PermissionsFilter implements ContainerRequestFilter {

	private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;
	private static final List<String> writerMethods = List.of(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE);
	private static final List<String> readerMethods = List.of(HttpMethod.GET);

	private final GracePeriodUtil lastSeen;

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
		}
		var permissionsUtil = getPermissionsUtil();
		var accessType = getAccessType(requestContext.getUriInfo().getPathSegments(), requestContext.getMethod());
		if (permissionsUtil.isAllowed(requestContext.getUriInfo().getPathSegments(), accessType, principal.getName())) {
			lastSeen.elementSeen(lastSeenKey);
			return;
		}

		abort(requestContext, "The requested action is forbidden by the permission policies");

	}

	private void abort(ContainerRequestContext requestContext, String reason) {
		requestContext.abortWith(Response.status(Status.FORBIDDEN)
				.entity(new ApiError(Status.FORBIDDEN.getStatusCode(), "AuthenticationException", reason)).build());
	}

	private AccessType getAccessType(List<PathSegment> pathSegments, String requestMethod) {
		if (pathSegments.stream().anyMatch(seg -> seg.getPath().equals(Constants.PERMISSIONS)))
			return AccessType.Manage;
		if (readerMethods.contains(requestMethod))
			return AccessType.Read;
		if (writerMethods.contains(requestMethod))
			return AccessType.Write;
		return AccessType.None;
	}

	protected PermissionsUtil getPermissionsUtil() {
		return new PermissionsUtil();
	}

}
