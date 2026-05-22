package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.security.ApiKeyAuthService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JWTSecurityContext;
import de.dlr.shepard.auth.security.JwtTokenAuthService;
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
    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      principal = jwtTokenAuthService.parseBearerToken(authorizationHeader);
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
