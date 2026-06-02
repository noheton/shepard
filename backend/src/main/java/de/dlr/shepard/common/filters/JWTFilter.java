package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.security.ApiKeyAuthService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JWTSecurityContext;
import de.dlr.shepard.auth.security.JwtTokenAuthService;
import de.dlr.shepard.auth.security.RoleChangedSinceTokenIssuedException;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.Constants;
import io.jsonwebtoken.ExpiredJwtException;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
@RequestScoped
public class JWTFilter implements ContainerRequestFilter {

  private AuthenticationContext authenticationContext;

  private JwtTokenAuthService jwtTokenAuthService;

  private ApiKeyAuthService apiKeyAuthService;

  JWTFilter() {}

  @Inject
  public JWTFilter(
    AuthenticationContext authenticationContext,
    JwtTokenAuthService jwtTokenAuthService,
    ApiKeyAuthService apiKeyAuthService
  ) {
    this.authenticationContext = authenticationContext;
    this.jwtTokenAuthService = jwtTokenAuthService;
    this.apiKeyAuthService = apiKeyAuthService;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (PublicEndpointRegistry.isRequestPathPublic(requestContext)) return;

    // Allow CORS preflight requests
    if (HttpMethod.OPTIONS.equals(requestContext.getMethod())) {
      return;
    }

    JWTPrincipal principal;

    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    String apiKeyHeader = requestContext.getHeaderString(Constants.API_KEY_HEADER);

    // MFFD-VIDEOREF-SCALE-1 — query-param JWT fallback for surfaces the browser
    // controls directly (HTML5 <video src>, <img src>, downloads triggered by
    // <a href download>). The browser cannot inject a custom Authorization header
    // on those elements, so they need a query-param channel. RFC 6750 §2.3
    // tolerates the URI query parameter form ("access_token") for the same reason.
    //
    // Precedence: explicit Authorization / X-API-KEY headers win over the query
    // param. The query param only applies when no header is present — so a
    // regular API caller using a Bearer header always takes the header path.
    //
    // Logging: the query param value is NEVER logged (it is the JWT). The
    // upstream WARN below only reports presence/absence of the header form.
    if ((authorizationHeader == null || authorizationHeader.isBlank()) && apiKeyHeader == null) {
      String queryToken = null;
      try {
        var queryParams = requestContext.getUriInfo().getQueryParameters();
        if (queryParams != null) {
          queryToken = queryParams.getFirst("access_token");
        }
      } catch (RuntimeException ignored) {
        // The reactive servlet layer may throw on certain malformed URIs.
        // The query-param channel is a best-effort fallback; on any
        // resolution failure we fall through to the no-token path so the
        // caller sees a normal 401 rather than a 500.
      }
      if (queryToken != null && !queryToken.isBlank()) {
        authorizationHeader = "Bearer " + queryToken;
      }
    }

    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      try {
        principal = jwtTokenAuthService.parseBearerToken(authorizationHeader);
      } catch (RoleChangedSinceTokenIssuedException ex) {
        // ROLE-GRANT-STALE-SESSION-02 — emit a structured 401 so the frontend
        // (UnauthorizedView via the role_changed stale-session prop) can
        // surface a specific "sign out + back in to refresh roles" prompt
        // distinct from generic invalid-token failures.
        Log.infof(
          "ROLE-GRANT-STALE-SESSION-02: rejecting stale-role JWT (iat=%dms, roleChangedAt=%dms)",
          ex.getTokenIssuedAtMillis(),
          ex.getRoleChangedAtMillis()
        );
        requestContext.abortWith(
          Response.status(Status.UNAUTHORIZED)
            .header(
              HttpHeaders.WWW_AUTHENTICATE,
              "Bearer error=\"role_changed\", error_description=\"Token predates role change\""
            )
            .entity(
              new ApiError(
                Status.UNAUTHORIZED.getStatusCode(),
                "role_changed",
                "Your session was issued before a role change. Sign out and back in to get the new role."
              )
            )
            .build()
        );
        return;
      }
    } else if (apiKeyHeader != null) {
      try {
        principal = apiKeyAuthService.parseApiKey(apiKeyHeader);
      } catch (ExpiredJwtException ex) {
        Log.warnf("API key expired: %s", ex.getMessage());
        requestContext.abortWith(
          Response.status(Status.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, "ApiKey error=\"expired\", error_description=\"API key expired\"")
            .entity(
              new ApiError(Status.UNAUTHORIZED.getStatusCode(), "AuthenticationException", "API key expired")
            )
            .build()
        );
        return;
      }
    } else {
      // Do not log the header values — they often contain credentials.
      Log.warnf(
        "Invalid/missing authorization header (Authorization=%s, X-API-KEY=%s) on endpoint %s",
        authorizationHeader == null ? "absent" : "present",
        apiKeyHeader == null ? "absent" : "present",
        requestContext.getUriInfo().getAbsolutePath()
      );
      requestContext.abortWith(
        Response.status(Status.UNAUTHORIZED)
          .entity(
            new ApiError(
              Status.UNAUTHORIZED.getStatusCode(),
              "AuthenticationException",
              "Invalid/missing authorization header"
            )
          )
          .build()
      );
      return;
    }
    if (principal == null) {
      requestContext.abortWith(
        Response.status(Status.UNAUTHORIZED)
          .entity(
            new ApiError(Status.UNAUTHORIZED.getStatusCode(), "AuthenticationException", "Invalid Authentication")
          )
          .build()
      );
      return;
    }

    var securityContext = new JWTSecurityContext(requestContext.getSecurityContext(), principal);
    requestContext.setSecurityContext(securityContext);
    authenticationContext.setPrincipal(principal);
  }
}
