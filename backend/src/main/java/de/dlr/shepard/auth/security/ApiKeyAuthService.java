package de.dlr.shepard.auth.security;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.PublicKey;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared shepard API-key (JWS) validation. Extracted from
 * {@link de.dlr.shepard.common.filters.JWTFilter} so non-JAX-RS routes
 * — notably the native MCP server at {@code /v2/mcp/*} — can validate
 * the same API keys without duplicating the signature / DB cross-check
 * logic that's on the CVE patch path.
 *
 * <p>An API key is a JWS issued by shepard itself, signed with the
 * deployment's private RSA key (held by {@link PKIHelper}). The JWS
 * is also stored verbatim on the matching {@link ApiKey} node so the
 * filter can cross-check that the presented token still corresponds
 * to a live key (rotation, revocation, role change all force a JWT
 * miss here).
 *
 * <p>Three failure outcomes:
 *
 * <ul>
 *   <li>signature invalid / payload garbage → {@code null}</li>
 *   <li>{@code exp} elapsed → {@link ExpiredJwtException} (propagated
 *       so the caller can emit a distinct {@code WWW-Authenticate:
 *       ApiKey error="expired"} 401)</li>
 *   <li>JWT looks valid but no matching DB row / role-set differs →
 *       {@code null}</li>
 * </ul>
 */
@ApplicationScoped
@Startup
public class ApiKeyAuthService {

  @Inject
  PKIHelper pkiHelper;

  @Inject
  ApiKeyService apiKeyService;

  @Inject
  ApiKeyLastSeenCache apiKeyLastSeenCache;

  /**
   * RSA public key cached at first successful access. {@link PKIHelper}
   * is {@code @RequestScoped} (it re-reads {@code ~/.shepard/keys/}
   * from disk per request, which is wasteful and — worse — fails when
   * we're called from a Vert.x route filter where CDI request scope
   * is not active. The key never changes at runtime, so once we've
   * loaded it once we keep it around.
   */
  private volatile PublicKey cachedPublicKey;

  /**
   * Warm the public-key cache at startup so the very first MCP request
   * that arrives via the Vert.x filter (where CDI request scope is not
   * active) doesn't 401 just because PKIHelper hasn't been touched yet.
   * We activate the request context manually for the duration of the
   * PKIHelper call.
   */
  @PostConstruct
  void warmCache() {
    ManagedContext rc = Arc.container().requestContext();
    boolean activatedHere = false;
    try {
      if (!rc.isActive()) {
        rc.activate();
        activatedHere = true;
      }
      resolvePublicKey();
    } finally {
      if (activatedHere) rc.terminate();
    }
  }

  /**
   * Returns {@code true} if the string looks like a shepard API-key
   * JWS — exactly three dot-separated base64url chunks. Cheap pre-flight
   * for the MCP filter that has to decide whether to try the OIDC
   * verifier or the API-key verifier on a bare {@code Bearer} value.
   */
  public static boolean looksLikeJws(String token) {
    if (token == null || token.isBlank()) return false;
    int dots = 0;
    for (int i = 0; i < token.length(); i++) if (token.charAt(i) == '.') dots++;
    return dots == 2;
  }

  /**
   * Validate an API-key JWS. Returns the resolved {@link JWTPrincipal}
   * or {@code null} on any failure. Throws {@link ExpiredJwtException}
   * if the JWT explicitly expired, so the caller can surface a
   * distinct 401 reason.
   *
   * @param token the raw JWS string (no scheme prefix)
   */
  public JWTPrincipal parseApiKey(String token) {
    Jws<Claims> jws = parseJws(token);
    if (jws == null) return null;
    JWTPrincipal principal = parsePrincipal(jws);
    if (principal == null) return null;

    UUID tokenId = UUID.fromString(jws.getBody().getId());

    if (apiKeyLastSeenCache.isKeyCached(tokenId.toString())) {
      return principal;
    }

    ApiKey storedKey = apiKeyService.getApiKey(tokenId);
    if (storedKey == null) {
      Log.warn("Token was not found in database");
      return null;
    }
    if (!storedKey.getJws().equals(token)) {
      Log.warn("Token from header is not equal to the token from database");
      return null;
    }

    Set<String> jwtRoles = readRolesFromJws(jws);
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
    return principal;
  }

  private Jws<Claims> parseJws(String token) {
    PublicKey key = resolvePublicKey();
    if (key == null) {
      Log.warn("API-key validation skipped — public key not available");
      return null;
    }
    try {
      Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
      Log.debugf("Valid token: %s", jws.getBody().getId());
      return jws;
    } catch (ExpiredJwtException ex) {
      throw ex;
    } catch (JwtException ex) {
      Log.warnf("Invalid token: %s", ex.getMessage());
      return null;
    }
  }

  /**
   * Return the cached public key, populating the cache on first call.
   * Tolerates {@code PKIHelper} being request-scoped and inaccessible
   * (Vert.x filter context) once the cache is warm — only the very
   * first call needs CDI scope.
   */
  private PublicKey resolvePublicKey() {
    PublicKey local = cachedPublicKey;
    if (local != null) return local;
    synchronized (this) {
      if (cachedPublicKey != null) return cachedPublicKey;
      try {
        pkiHelper.init();
        cachedPublicKey = pkiHelper.getPublicKey();
        return cachedPublicKey;
      } catch (RuntimeException ex) {
        Log.warnf("Could not initialise PKI public key: %s", ex.getMessage());
        return null;
      }
    }
  }

  /** Test-only seam — let tests inject the key directly without going through CDI. */
  void primeCachedPublicKey(PublicKey key) {
    this.cachedPublicKey = key;
  }

  /** Build a principal from a validated API-key JWS (no DB check). */
  public JWTPrincipal parsePrincipal(Jws<Claims> jws) {
    var body = jws.getBody();
    String subject = body.getSubject();
    String keyId = body.getId();
    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }
    return new JWTPrincipal(null, null, subject, keyId, readRolesFromJws(jws));
  }

  static Set<String> readRolesFromJws(Jws<Claims> jws) {
    Object claim = jws.getBody().get("roles");
    if (!(claim instanceof Collection<?> col)) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (Object item : col) {
      if (item instanceof String s && !s.isBlank()) out.add(s);
    }
    return out;
  }
}
