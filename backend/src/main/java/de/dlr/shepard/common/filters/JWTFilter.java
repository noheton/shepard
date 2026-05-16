package de.dlr.shepard.common.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.security.ApiKeyLastSeenCache;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.security.JWTSecurityContext;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.AUTHENTICATION)
@RequestScoped
public class JWTFilter implements ContainerRequestFilter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PublicKey jwtPublicKey;

  private PublicKey oidcPublicKey;

  /**
   * Legacy hard-rejection role (pre-F8). When set, a JWT that does
   * NOT carry this role under {@code realm_access.roles} is rejected
   * outright. F8's `roles-claim-path` is independent — it controls
   * which JWT path the filter walks to find role-strings; this
   * setting controls whether to reject when the role isn't present.
   */
  private String requiredOidcRole;

  /**
   * F8 — the dot-path inside the JWT body that carries the role
   * strings. Default {@code "realm_access.roles"} matches Keycloak's
   * shape. Operators on Pocket ID / Authentik / Azure AD set this
   * to e.g. {@code "groups"} or {@code "roles"}.
   */
  private String[] rolesClaimPath;

  /**
   * The role-string in the IdP claim that maps to shepard's
   * {@code instance-admin} role. Empty (default) means "no IdP-side
   * grant in this deployment" — only Neo4j-internal {@code :HAS_ROLE}
   * edges grant the role. Per aidocs/51 §3.2.
   */
  private String instanceAdminClaimValue;

  private ApiKeyLastSeenCache apiKeyLastSeenCache;

  private ApiKeyService apiKeyService;

  private AuthenticationContext authenticationContext;

  private RoleDAO roleDAO;

  JWTFilter() {}

  @Inject
  public JWTFilter(
    PKIHelper pkiHelper,
    ApiKeyService apiKeyService,
    ApiKeyLastSeenCache apiKeyLastSeenCache,
    AuthenticationContext authenticationContext,
    RoleDAO roleDAO,
    @ConfigProperty(name = "oidc.public") String oidcPublic,
    @ConfigProperty(name = "oidc.role") Optional<String> oidcRole,
    @ConfigProperty(name = "shepard.oidc.roles-claim-path", defaultValue = "realm_access.roles") String rolesClaimPath,
    @ConfigProperty(name = "shepard.instance-admin.role") Optional<String> instanceAdminClaimValue
  ) throws NoSuchAlgorithmException, InvalidKeySpecException, IllegalArgumentException {
    try {
      this.apiKeyService = apiKeyService;
      this.apiKeyLastSeenCache = apiKeyLastSeenCache;
      this.authenticationContext = authenticationContext;
      this.roleDAO = roleDAO;
      this.requiredOidcRole = oidcRole.orElse("");
      this.rolesClaimPath = parseClaimPath(rolesClaimPath);
      this.instanceAdminClaimValue = instanceAdminClaimValue.map(String::trim).orElse("");

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
    // literal substring "Bearer " mid-payload (M4 fix).
    String token = header.startsWith("Bearer ") ? header.substring(7) : header;
    // A0 dropped the hardcoded `realm_access` Jackson deserializer hint —
    // F8 walks the configured `shepard.oidc.roles-claim-path` manually
    // via Jackson dot-path lookup (in parsePrincipalFromAccessToken), so
    // the parser stays generic.
    var parser = Jwts.parserBuilder().setSigningKey(oidcPublicKey).build();

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

  JWTPrincipal parsePrincipalFromAccessToken(Jws<Claims> jws) {
    var body = jws.getBody();
    String keyId = body.getId();
    String subject = body.getSubject();
    String audience = body.getAudience();
    String issuedFor = body.get("azp", String.class);

    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }

    // F8 — collect role-strings via the configured claim path.
    List<String> idpRoles = collectRolesFromClaim(body);

    // Legacy `oidc.role` rejection — preserved for backward
    // compatibility with deployments that relied on hard-deny.
    if (!requiredOidcRole.isBlank()) {
      boolean hasRequired = idpRoles.stream().anyMatch(r -> r.equals(requiredOidcRole));
      if (!hasRequired) {
        Log.warnf("User is missing required role: %s", requiredOidcRole);
        return null;
      }
    }

    // We only want the last part of the subject, since this is usually a human
    // readable user name
    var splitted = subject.split(":");
    String username = splitted[splitted.length - 1];

    // Dual-source role resolution per aidocs/51 §3.3.
    Set<String> resolvedRoles = resolveDualSourceRoles(username, idpRoles);

    // F4 — extract iat (issued-at) for the cache-key 4th dimension.
    // body.getIssuedAt() returns null when the JWT omits the claim; fall back
    // to 0L so the principal is still valid but shares the zero-iat cache slot
    // with API-key principals (conservative: stale entry would be from a
    // token with no iat, which we can't do better for anyway).
    java.util.Date issuedAt = body.getIssuedAt();
    long iat = issuedAt != null ? issuedAt.getTime() / 1000L : 0L;

    return new JWTPrincipal(audience, issuedFor, username, keyId, resolvedRoles, iat);
  }

  /**
   * Walk the configured dot-path through the JWT body and collect any
   * string values found at the leaf. Tolerates:
   *
   * <ul>
   *   <li>a string array (Keycloak shape: {@code realm_access.roles})
   *   <li>a single string
   *   <li>missing intermediate keys (returns an empty list)
   *   <li>non-array, non-string leaf (returns an empty list)
   * </ul>
   */
  List<String> collectRolesFromClaim(Claims body) {
    if (rolesClaimPath == null || rolesClaimPath.length == 0) return List.of();
    JsonNode node = MAPPER.valueToTree(body);
    for (String segment : rolesClaimPath) {
      if (node == null || node.isMissingNode() || node.isNull()) return List.of();
      node = node.get(segment);
    }
    if (node == null || node.isMissingNode() || node.isNull()) return List.of();
    List<String> out = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(item -> {
        if (item.isTextual()) out.add(item.asText());
      });
    } else if (node.isTextual()) {
      out.add(node.asText());
    }
    return out;
  }

  /**
   * Combine IdP-claim role-strings with shepard-internal
   * {@code :HAS_ROLE} grants, producing the deduplicated principal
   * roles list. Today the only mapped role-string is the configured
   * {@code shepard.instance-admin.role}; future tiers parallel.
   */
  Set<String> resolveDualSourceRoles(String username, Collection<String> idpRoles) {
    Set<String> out = new LinkedHashSet<>();
    if (
      !instanceAdminClaimValue.isBlank() &&
      idpRoles != null &&
      idpRoles.stream().anyMatch(r -> r.equals(instanceAdminClaimValue))
    ) {
      out.add(Constants.INSTANCE_ADMIN_ROLE);
    }
    if (roleDAO != null) {
      try {
        out.addAll(roleDAO.rolesForUser(username));
      } catch (RuntimeException ex) {
        // Non-fatal — Neo4j hiccup shouldn't deny a valid OIDC login;
        // the principal just won't carry the Neo4j-sourced grants
        // until the next request.
        Log.warnf("Failed to load Neo4j role grants for %s: %s", username, ex.getMessage());
      }
    }
    return out;
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

      // A0 §4.3 — cross-check JWT roles claim against stored Set<String>.
      // A mismatch is treated as a forged token (401).
      Set<String> jwtRoles = readApiKeyRolesFromJws(jws);
      Set<String> storedRoles = storedKey.getRoles() == null ? Set.of() : storedKey.getRoles();
      if (!jwtRoles.equals(storedRoles)) {
        Log.warnf(
          "API-key roles mismatch (JWT=%s, stored=%s) — treating as invalid token",
          jwtRoles,
          storedRoles
        );
        return null;
      }

      apiKeyLastSeenCache.cacheKey(tokenId.toString());
    }
    return principal;
  }

  JWTPrincipal parsePrincipalFromApiKey(Jws<Claims> jws) {
    var body = jws.getBody();
    String subject = body.getSubject();
    String keyId = body.getId();
    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }

    Set<String> rolesFromJwt = readApiKeyRolesFromJws(jws);
    return new JWTPrincipal(null, null, subject, keyId, rolesFromJwt);
  }

  static Set<String> readApiKeyRolesFromJws(Jws<Claims> jws) {
    Object claim = jws.getBody().get("roles");
    if (!(claim instanceof Collection<?> col)) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (Object item : col) {
      if (item instanceof String s && !s.isBlank()) out.add(s);
    }
    return out;
  }

  static String[] parseClaimPath(String path) {
    if (path == null || path.isBlank()) return new String[0];
    String[] parts = path.split("\\.");
    return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }
}
