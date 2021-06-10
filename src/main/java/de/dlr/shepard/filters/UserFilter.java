package de.dlr.shepard.filters;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.util.Constants;
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
