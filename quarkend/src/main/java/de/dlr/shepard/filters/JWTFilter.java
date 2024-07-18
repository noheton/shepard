// TODO: Activate filter again
/*
package de.dlr.shepard.filters;

import de.dlr.shepard.exceptions.ApiError;
import de.dlr.shepard.neo4Core.services.ApiKeyService;
import de.dlr.shepard.security.GracePeriodUtil;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.security.JWTSecurityContext;
import de.dlr.shepard.security.RolesList;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.PKIHelper;
import de.dlr.shepard.util.PropertiesHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import jakarta.annotation.Priority;
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
import lombok.extern.slf4j.Slf4j;

@Provider
@Priority(Priorities.AUTHENTICATION)
@Slf4j
public class JWTFilter implements ContainerRequestFilter {

  private static final int FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

  private final PublicKey oidcPublicKey;
  private final PublicKey jwtPublicKey;
  private final String role;
  private GracePeriodUtil lastSeen = new GracePeriodUtil(FIVE_MINUTES_IN_MILLIS);

  public JWTFilter() throws NoSuchAlgorithmException, InvalidKeySpecException, IllegalArgumentException {
    var pHelper = new PropertiesHelper();
    var kFactory = KeyFactory.getInstance("RSA");
    byte[] kcDecoded;
    try {
      kcDecoded = Base64.getDecoder().decode(pHelper.getProperty("oidc.public"));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The given oidc public key is invalid", e);
    }
    var kcSpec = new X509EncodedKeySpec(kcDecoded);
    oidcPublicKey = kFactory.generatePublic(kcSpec);

    var pkiHelper = new PKIHelper();
    pkiHelper.init();
    jwtPublicKey = pkiHelper.getPublicKey();

    role = pHelper.getProperty("oidc.role");
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
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
      principal = parseApiKey(apiKeyHeader);
    } else {
      log.warn(
        "Invalid/missing authorization header (Authorization: {}, X-API-KEY: {}) on endpoint {}",
        authorizationHeader,
        apiKeyHeader,
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
    // Extract the token from the HTTP Authorization header
    String token = header.replace("Bearer ", "");
    var parser = Jwts.parserBuilder()
      .setSigningKey(oidcPublicKey)
      .deserializeJsonWith(new JacksonDeserializer<>(Map.of("realm_access", RolesList.class)))
      .build();

    Jws<Claims> jws;

    try {
      jws = parser.parseClaimsJws(token);
      log.debug("Valid token: {}", jws.getBody().getId());
    } catch (JwtException ex) {
      log.warn("Invalid token: {}", ex.getMessage());
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
      log.warn("Token is missing a subject");
      return null;
    }

    // Read realm roles
    if (!role.isBlank()) {
      var realmRoles = realmAccess.map(RolesList::getRoles).orElse(new String[0]);
      var hasRole = Arrays.stream(realmRoles).anyMatch(r -> r.equals(role));
      if (!hasRole) {
        log.warn("User is missing required role: {}", role);
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
      log.debug("Valid token: {}", jws.getBody().getId());
    } catch (JwtException ex) {
      log.warn("Invalid token: {}", ex.getMessage());
      return null;
    }
    return jws;
  }

  private JWTPrincipal parseApiKey(String header) {
    ApiKeyService apiKeyService = getApiKeyService();
    JWTPrincipal principal = null;
    Jws<Claims> jws = parseApiKeyFromHeader(header);
    if (jws != null) {
      principal = parsePrincipalFromApiKey(jws);
      if (principal == null) return null;
      UUID tokenId = UUID.fromString(jws.getBody().getId());

      if (lastSeen.elementIsKnown(tokenId.toString())) {
        return principal;
      }

      var storedKey = apiKeyService.getApiKey(tokenId);
      if (storedKey == null) {
        log.warn("Token was not found in database");
        return null;
      } else if (!storedKey.getJws().equals(header)) {
        log.warn("Token from header is not equal to the token from database");
        return null;
      }
      lastSeen.elementSeen(tokenId.toString());
    }
    return principal;
  }

  private JWTPrincipal parsePrincipalFromApiKey(Jws<Claims> jws) {
    var body = jws.getBody();
    String subject = body.getSubject();
    String keyId = body.getId();
    if (subject == null || subject.isEmpty()) {
      log.warn("Token is missing a subject");
      return null;
    }

    var principal = new JWTPrincipal(subject, keyId);
    return principal;
  }

  protected ApiKeyService getApiKeyService() {
    return new ApiKeyService();
  }
}
*/
