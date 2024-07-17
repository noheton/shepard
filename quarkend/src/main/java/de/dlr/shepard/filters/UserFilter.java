// TODO: Activate filter again
/*
package de.dlr.shepard.filters;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.exceptions.ShepardProcessingException;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.Userinfo;
import de.dlr.shepard.security.UserinfoService;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@Priority(Priorities.AUTHENTICATION + 1)
public class UserFilter implements ContainerRequestFilter {

  private static final int THIRTY_MINUTES_IN_MILLIS = 30 * 60 * 1000;

  private final GracePeriodUtil lastSeen;

  public UserFilter() {
    lastSeen = new GracePeriodUtil(THIRTY_MINUTES_IN_MILLIS);
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    UserService userService = getUserService();

    var principal = requestContext.getSecurityContext().getUserPrincipal();
    if (!(principal instanceof JWTPrincipal)) {
      log.warn("Unknown principal {}", principal);
      abort(requestContext, "User could not be read from the request context");
      return;
    }
    var jwtPrincipal = (JWTPrincipal) principal;
    var header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ") && !lastSeen.elementIsKnown(jwtPrincipal.getUsername())) {
      User user;
      Userinfo userinfo;
      try {
        userinfo = getUserinfoService().fetchUserinfo(header);
      } catch (ShepardProcessingException e) {
        abort(requestContext, "User info could not be retrieved");
        return;
      }
      user = parseUserFromUserinfo(userinfo);
      if (!jwtPrincipal.getUsername().equals(user.getUsername())) {
        log.warn("The usernames from the access token and the userinfo response do not match");
        abort(requestContext, "The usernames from the access token and the userinfo response do not match");
        return;
      }
      var created = userService.updateUser(user);
      if (created == null) {
        log.warn("The user could not be updated or created");
        abort(requestContext, "The user could not be updated or created");
        return;
      }
      lastSeen.elementSeen(jwtPrincipal.getUsername());
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

  protected UserService getUserService() {
    return new UserService();
  }

  protected UserinfoService getUserinfoService() {
    return new UserinfoService();
  }
}
*/
