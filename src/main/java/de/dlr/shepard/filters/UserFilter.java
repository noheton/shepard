package de.dlr.shepard.filters;

import java.io.IOException;

import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.util.Constants;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.log4j.Log4j2;

@Provider
@Log4j2
@Priority(Priorities.AUTHENTICATION + 1)
public class UserFilter implements ContainerRequestFilter {

	private static final int THIRTY_MINUTES_IN_MILLIS = 30 * 60 * 1000;

	private static GracePeriodUtil<?> lastSeen;

	public UserFilter() {
		lastSeen = new GracePeriodUtil<>(THIRTY_MINUTES_IN_MILLIS);
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		UserService userService = getService();

		var principal = requestContext.getProperty(Constants.USER);
		if (principal == null || !(principal instanceof JWTPrincipal)) {
			log.warn("Unknown principal {}", principal);
			return;
		}
		var jwtPrincipal = (JWTPrincipal) principal;

		if (!lastSeen.elementIsKnown(jwtPrincipal.getUsername())) {
			lastSeen.elementSeen(jwtPrincipal.getUsername(), null);
			userService.updateUser(jwtPrincipal);
		}

	}

	protected UserService getService() {
		return new UserService();
	}

}
