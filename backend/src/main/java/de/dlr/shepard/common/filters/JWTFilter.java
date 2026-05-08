package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.auth.security.ApiKeyLastSeenCache;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JWTSecurityContext;
import de.dlr.shepard.auth.security.RolesList;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
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
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.AUTHENTICATION)
@RequestScoped
public class JWTFilter implements ContainerRequestFilter {

  private PublicKey jwtPublicKey;

  private PublicKey oidcPublicKey;

  private String role;

  private ApiKeyLastSeenCache apiKeyLastSeenCache;

  private ApiKeyService apiKeyService;

  private AuthenticationContext authenticationContext;

  JWTFilter() {}

  @Inject
  public JWTFilter(
    PKIHelper pkiHelper,
    ApiKeyService apiKeyService,
    ApiKeyLastSeenCache apiKeyLastSeenCache,
    AuthenticationContext authenticationContext,
    @ConfigProperty(name = "oidc.public") String oidcPublic,
    @ConfigProperty(name = "oidc.role") Optional<String> oidcRole
  ) throws NoSuchAlgorithmException, InvalidKeySpecException, IllegalArgumentException {
    try {
      this.apiKeyService = apiKeyService;
      this.apiKeyLastSeenCache = apiKeyLastSeenCache;
      this.authenticationContext = authenticationContext;
      this.role = oidcRole.orElse("");

      var kFactory = KeyFactory.getInstance("RSA");
      byte[] kcDecoded;
      try {
        kcDecoded = Base64.getDecoder().decode(oidcPublic);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("The given oidc public key is invalid", e);
      }
      var kcSpec = new X509EncodedKeySpec(kcDecoded);
      oidcPublicKey = kFactory.generatePublic(kcSpec);

      pkiHelper.init();
      jwtPublicKey = pkiHelper.getPublicKey();
    } catch (Exception ex) {
      Log.fatalf("Cannot create instance of JWTFilter: %s", ex.getMessage());
      throw ex;
    }
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (PublicEndpointRegistry.isRequestPathPublic(requestContext)) return;

    // Allow CORS preflight requests
    if (HttpMethod.OPTIONS.equals(requestContext.getMethod())) {
      // Allow all requests with request method OPTIONS
      return;
    }

    JWTPrincipal principal;

    // Get the HTTP Authorization header from the request
    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    String apiKeyHeader = requestContext.getHeaderString(Constants.API_KEY_HEADER);
    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      principal = parseAccessToken(authorizationHeader);
    } else if (apiKeyHeader != null) {
      try {
        principal = parseApiKey(apiKeyHeader);
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
      // Recording only presence is enough to triage missing-auth vs
      // wrong-scheme; full payloads would be a token-leak vector.
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

  private JWTPrincipal parseAccessToken(String header) {
    JWTPrincipal result = null;
    Jws<Claims> jws = parseAccessTokenFromHeader(header);
    if (jws != null) {
      result = parsePrincipalFromAccessToken(jws);
    }
    return result;
  }

  private Jws<Claims> parseAccessTokenFromHeader(String header) {
    // Caller has verified the "Bearer " prefix, so substring(7) is safe.
    // Using replace() instead would mangle any token that contains the
    // literal substring "Bearer " mid-payload.
    String token = header.startsWith("Bearer ") ? header.substring(7) : header;
    var parser = Jwts.parserBuilder()
      .setSigningKey(oidcPublicKey)
      .deserializeJsonWith(new JacksonDeserializer<>(Map.of("realm_access", RolesList.class)))
      .build();

    Jws<Claims> jws;

    try {
      jws = parser.parseClaimsJws(token);
      Log.debugf("Valid token: %s", jws.getBody().getId());
    } catch (JwtException ex) {
      Log.warnf("Invalid token: %s", ex.getMessage());
      return null;
    }
    return jws;
  }

  private JWTPrincipal parsePrincipalFromAccessToken(Jws<Claims> jws) {
    var body = jws.getBody();
    String keyId = body.getId();
    String subject = body.getSubject();
    String audience = body.getAudience();
    String issuedFor = body.get("azp", String.class);
    Optional<RolesList> realmAccess = Optional.ofNullable(body.get("realm_access", RolesList.class));

    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }

    // Read realm roles
    if (!role.isBlank()) {
      var realmRoles = realmAccess.map(RolesList::getRoles).orElse(new String[0]);
      var hasRole = Arrays.stream(realmRoles).anyMatch(r -> r.equals(role));
      if (!hasRole) {
        Log.warnf("User is missing required role: %s", role);
        return null;
      }
    }

    // We only want the last part of the subject, since this is usually a human
    // readable user name
    var splitted = subject.split(":");
    String username = splitted[splitted.length - 1];

    var principal = new JWTPrincipal(audience, issuedFor, username, keyId, new String[0]);

    return principal;
  }

  private Jws<Claims> parseApiKeyFromHeader(String token) {
    // Extract the api key from the HTTP Authorization header
    Jws<Claims> jws;

    try {
      jws = Jwts.parserBuilder().setSigningKey(jwtPublicKey).build().parseClaimsJws(token);
      Log.debugf("Valid token: %s", jws.getBody().getId());
    } catch (ExpiredJwtException ex) {
      // surfaced to the filter so it can emit a distinct "expired" 401
      throw ex;
    } catch (JwtException ex) {
      Log.warnf("Invalid token: %s", ex.getMessage());
      return null;
    }
    return jws;
  }

  private JWTPrincipal parseApiKey(String header) {
    JWTPrincipal principal = null;
    Jws<Claims> jws = parseApiKeyFromHeader(header);
    if (jws != null) {
      principal = parsePrincipalFromApiKey(jws);
      if (principal == null) return null;
      UUID tokenId = UUID.fromString(jws.getBody().getId());

      if (apiKeyLastSeenCache.isKeyCached(tokenId.toString())) {
        return principal;
      }

      var storedKey = apiKeyService.getApiKey(tokenId);
      if (storedKey == null) {
        Log.warn("Token was not found in database");
        return null;
      } else if (!storedKey.getJws().equals(header)) {
        Log.warn("Token from header is not equal to the token from database");
        return null;
      }
      apiKeyLastSeenCache.cacheKey(tokenId.toString());
    }
    return principal;
  }

  private JWTPrincipal parsePrincipalFromApiKey(Jws<Claims> jws) {
    var body = jws.getBody();
    String subject = body.getSubject();
    String keyId = body.getId();
    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }

    var principal = new JWTPrincipal(subject, keyId);
    return principal;
  }
}
