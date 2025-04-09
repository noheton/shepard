package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.UserLastSeenCache;
import de.dlr.shepard.auth.security.Userinfo;
import de.dlr.shepard.auth.security.UserinfoService;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.exceptions.ShepardProcessingException;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION + 1)
@ApplicationScoped
public class UserFilter implements ContainerRequestFilter {

  @Inject
  UserLastSeenCache userLastSeenCache;

  @Inject
  UserService userService;

  @Inject
  UserinfoService userInfoService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (PublicEndpointRegistry.isRequestPathPublic(requestContext)) return;
    var principal = requestContext.getSecurityContext().getUserPrincipal();
    if (!(principal instanceof JWTPrincipal)) {
      Log.warnf("Unknown principal %s", principal);
      abort(requestContext, "User could not be read from the request context");
      return;
    }
    var jwtPrincipal = (JWTPrincipal) principal;
    var header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ") && !userLastSeenCache.isKeyCached(jwtPrincipal.getUsername())) {
      User user;
      Userinfo userinfo;
      try {
        userinfo = userInfoService.fetchUserinfo(header);
      } catch (ShepardProcessingException e) {
        abort(requestContext, "User info could not be retrieved");
        return;
      }
      user = parseUserFromUserinfo(userinfo);
      if (!jwtPrincipal.getUsername().equals(user.getUsername())) {
        Log.warn("The usernames from the access token and the userinfo response do not match");
        abort(requestContext, "The usernames from the access token and the userinfo response do not match");
        return;
      }
      var created = userService.createOrUpdateUser(user);
      if (created == null) {
        Log.warn("The user could not be updated or created");
        abort(requestContext, "The user could not be updated or created");
        return;
      }
      userLastSeenCache.cacheKey(jwtPrincipal.getUsername());
    }
  }

  private void abort(ContainerRequestContext requestContext, String reason) {
    requestContext.abortWith(
      Response.status(Status.UNAUTHORIZED)
        .entity(new ApiError(Status.UNAUTHORIZED.getStatusCode(), "AuthenticationException", reason))
        .build()
    );
  }

  private User parseUserFromUserinfo(Userinfo userinfo) {
    // We only want the last part of the subject, since this is usually a human
    // readable user name
    var splitted = userinfo.getSub().split(":");
    String username = splitted[splitted.length - 1];

    User user = new User(username, userinfo.getGivenName(), userinfo.getFamilyName(), userinfo.getEmail());
    return user;
  }
}
