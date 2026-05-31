package de.dlr.shepard.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Shared OIDC Bearer token validation extracted from {@link
 * de.dlr.shepard.common.filters.JWTFilter} so that non-JAX-RS routes
 * (notably the Quarkus MCP server's Vert.x routes under {@code /v2/mcp/*})
 * can reuse the exact same JWT validation, role-claim walk, and
 * dual-source role resolution without duplicating logic that's on the
 * CVE patch path.
 *
 * <p>Scope: this service handles ONLY the OIDC Bearer token path.
 * Shepard API keys (signed with the internal PKI from {@code PKIHelper})
 * stay in {@code JWTFilter} because they depend on request-scoped
 * machinery that JAX-RS filters already participate in.
 */
@ApplicationScoped
public class JwtTokenAuthService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final PublicKey oidcPublicKey;
  private final String requiredOidcRole;
  private final String[] rolesClaimPath;
  private final String instanceAdminClaimValue;

  /**
   * Optional JWT claim name used to extract the identity string for
   * {@link JWTPrincipal#getUsername()}. When empty (the default) the
   * existing sub-split behaviour is preserved: the JWT {@code sub} claim
   * is split on {@code :} and the last segment is used as the username.
   * Operators on non-Keycloak IdPs that emit a human-readable identity
   * in a different claim (e.g. {@code preferred_username}, {@code email},
   * {@code upn}) set {@code shepard.oidc.username-claim} to the claim
   * name they need.
   *
   * <p><strong>Migration note.</strong> Flipping this key on an existing
   * deployment changes the identity string for every user. Neo4j
   * {@code :User} nodes are keyed by whatever string was used as username
   * at first login; switching the claim re-creates users under new keys
   * and loses Neo4j-internal role grants and permission records. Perform
   * a username-migration pass in Neo4j before enabling the new claim in
   * production.
   */
  private final String usernameClaim;

  @Inject
  RoleDAO roleDAO;

  /**
   * ROLE-GRANT-STALE-SESSION-02 — request-scoped DAO used to read the
   * affected user's {@code roleChangedAt} stamp on every JWT parse. Null
   * in test-only constructors that don't exercise the gate; in that case
   * {@link #lookupRoleChangedAtMillis(String)} returns {@code null} and
   * the gate is a no-op.
   */
  @Inject
  UserDAO userDAO;

  /** Required by the CDI runtime for {@code @ApplicationScoped} proxy generation. */
  protected JwtTokenAuthService() {
    this.oidcPublicKey = null;
    this.requiredOidcRole = "";
    this.rolesClaimPath = new String[0];
    this.instanceAdminClaimValue = "";
    this.usernameClaim = "";
  }

  @Inject
  public JwtTokenAuthService(
    @ConfigProperty(name = "oidc.public") String oidcPublic,
    @ConfigProperty(name = "oidc.role") Optional<String> oidcRole,
    @ConfigProperty(
      name = "shepard.oidc.roles-claim-path",
      defaultValue = "realm_access.roles"
    ) String rolesClaimPath,
    @ConfigProperty(name = "shepard.instance-admin.role") Optional<String> instanceAdminClaimValue,
    @ConfigProperty(name = "shepard.oidc.username-claim", defaultValue = "") Optional<String> usernameClaim
  ) throws NoSuchAlgorithmException, InvalidKeySpecException {
    this.requiredOidcRole = oidcRole.orElse("");
    this.rolesClaimPath = parseClaimPath(rolesClaimPath);
    this.instanceAdminClaimValue = instanceAdminClaimValue.map(String::trim).orElse("");
    this.usernameClaim = usernameClaim.map(String::trim).orElse("");

    var kFactory = KeyFactory.getInstance("RSA");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(oidcPublic);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The given oidc public key is invalid", e);
    }
    this.oidcPublicKey = kFactory.generatePublic(new X509EncodedKeySpec(decoded));
  }

  /**
   * Test-only constructor (no username-claim override — uses sub-split default).
   * Delegates to the 6-arg form with an empty username-claim.
   */
  JwtTokenAuthService(
    PublicKey oidcPublicKey,
    String requiredOidcRole,
    String[] rolesClaimPath,
    String instanceAdminClaimValue,
    RoleDAO roleDAO
  ) {
    this(oidcPublicKey, requiredOidcRole, rolesClaimPath, instanceAdminClaimValue, "", roleDAO);
  }

  /** Test-only constructor with explicit username-claim override. */
  JwtTokenAuthService(
    PublicKey oidcPublicKey,
    String requiredOidcRole,
    String[] rolesClaimPath,
    String instanceAdminClaimValue,
    String usernameClaim,
    RoleDAO roleDAO
  ) {
    this(oidcPublicKey, requiredOidcRole, rolesClaimPath, instanceAdminClaimValue, usernameClaim, roleDAO, null);
  }

  /**
   * Test-only constructor that also injects a {@link UserDAO} so the
   * ROLE-GRANT-STALE-SESSION-02 gate can be exercised in unit tests.
   * Production code uses CDI injection of both DAOs.
   */
  JwtTokenAuthService(
    PublicKey oidcPublicKey,
    String requiredOidcRole,
    String[] rolesClaimPath,
    String instanceAdminClaimValue,
    String usernameClaim,
    RoleDAO roleDAO,
    UserDAO userDAO
  ) {
    this.oidcPublicKey = oidcPublicKey;
    this.requiredOidcRole = requiredOidcRole == null ? "" : requiredOidcRole;
    this.rolesClaimPath = rolesClaimPath == null ? new String[0] : rolesClaimPath;
    this.instanceAdminClaimValue =
      instanceAdminClaimValue == null ? "" : instanceAdminClaimValue.trim();
    this.usernameClaim = usernameClaim == null ? "" : usernameClaim.trim();
    this.roleDAO = roleDAO;
    this.userDAO = userDAO;
  }

  /**
   * Validate the {@code Authorization: Bearer <jwt>} header. Returns the
   * resolved principal on success, {@code null} on any failure
   * (signature, parsing, claims, missing subject, legacy role gate).
   *
   * <p>Mirrors the behavior of {@code JWTFilter.parseAccessToken} so
   * callers see identical authn outcomes.
   */
  public JWTPrincipal parseBearerToken(String authorizationHeader) {
    if (authorizationHeader == null) return null;
    Jws<Claims> jws = parseAccessTokenFromHeader(authorizationHeader);
    if (jws == null) return null;
    return parsePrincipalFromAccessToken(jws);
  }

  Jws<Claims> parseAccessTokenFromHeader(String header) {
    String token = header.startsWith("Bearer ") ? header.substring(7) : header;
    var parser = Jwts.parserBuilder().setSigningKey(oidcPublicKey).build();
    try {
      Jws<Claims> jws = parser.parseClaimsJws(token);
      Log.debugf("Valid token: %s", jws.getBody().getId());
      return jws;
    } catch (ExpiredJwtException ex) {
      // Expired tokens are normal client-side staleness (rotation lag, probe
      // traffic on public endpoints like /versionz). DEBUG — not a security
      // event. Signature mismatches / malformed tokens stay at WARN below.
      Log.debugf("Expired token presented: %s", ex.getMessage());
      return null;
    } catch (JwtException ex) {
      Log.warnf("Invalid token: %s", ex.getMessage());
      return null;
    }
  }

  public JWTPrincipal parsePrincipalFromAccessToken(Jws<Claims> jws) {
    var body = jws.getBody();
    String keyId = body.getId();
    String subject = body.getSubject();
    String audience = body.getAudience();
    String issuedFor = body.get("azp", String.class);

    if (subject == null || subject.isEmpty()) {
      Log.warn("Token is missing a subject");
      return null;
    }

    List<String> idpRoles = collectRolesFromClaim(body);

    if (!requiredOidcRole.isBlank()) {
      boolean hasRequired = idpRoles.stream().anyMatch(r -> r.equals(requiredOidcRole));
      if (!hasRequired) {
        Log.warnf("User is missing required role: %s", requiredOidcRole);
        return null;
      }
    }

    String username = extractUsername(body, subject);

    java.util.Date issuedAt = body.getIssuedAt();
    long iat = issuedAt != null ? issuedAt.getTime() / 1000L : 0L;

    // ROLE-GRANT-STALE-SESSION-02 — reject tokens that pre-date the user's
    // most recent role mutation so the user is forced to re-auth and pick
    // up the new role set. The Neo4j read is the same cost as the role-
    // lookup below; we accept the per-request cost. Throws so the filter
    // can produce a 401 with a structured `error: "role_changed"` body
    // distinct from generic invalid-token responses.
    Long roleChangedAtMillis = lookupRoleChangedAtMillis(username);
    if (roleChangedAtMillis != null && iat > 0L) {
      long iatMillis = iat * 1000L;
      if (iatMillis < roleChangedAtMillis) {
        Log.warnf(
          "Rejecting JWT for '%s': iat=%dms predates roleChangedAt=%dms",
          username,
          iatMillis,
          roleChangedAtMillis
        );
        throw new RoleChangedSinceTokenIssuedException(iatMillis, roleChangedAtMillis);
      }
    }

    Set<String> resolvedRoles = resolveDualSourceRoles(username, idpRoles);

    return new JWTPrincipal(audience, issuedFor, username, keyId, resolvedRoles, iat);
  }

  /**
   * ROLE-GRANT-STALE-SESSION-02 — read the user's {@code roleChangedAt}
   * stamp from Neo4j. Returns {@code null} when the user is unknown (the
   * principal will still be minted; the gate only fires on known users)
   * OR the field is unset (pre-feature rows) OR the DAO is unavailable
   * (test setups without UserDAO injection). Defensive try/catch so a
   * Neo4j hiccup on the gate read does not break authentication
   * altogether — a transient DAO error simply lets the token through
   * (fail-open on a secondary check; the primary signature gate has
   * already passed).
   */
  Long lookupRoleChangedAtMillis(String username) {
    if (userDAO == null || username == null || username.isBlank()) return null;
    try {
      User u = userDAO.find(username);
      if (u == null) return null;
      java.util.Date stamp = u.getRoleChangedAt();
      return stamp == null ? null : stamp.getTime();
    } catch (RuntimeException ex) {
      Log.warnf("Failed to load roleChangedAt for %s: %s", username, ex.getMessage());
      return null;
    }
  }

  /**
   * Extract the username identity string from the JWT body.
   *
   * <p>When {@link #usernameClaim} is set, the named claim is read from
   * the JWT body; if it is present and textual, its value is returned.
   * If the claim is absent or non-textual, a warning is emitted and the
   * method falls back to the sub-split path below.
   *
   * <p>Default (empty {@code usernameClaim}): split {@code subject} on
   * {@code :} and return the last segment — the longstanding Keycloak-
   * compatible behaviour.
   */
  String extractUsername(Claims body, String subject) {
    if (usernameClaim != null && !usernameClaim.isBlank()) {
      Object raw = body.get(usernameClaim);
      if (raw instanceof String s && !s.isBlank()) {
        return s;
      }
      Log.warnf(
        "shepard.oidc.username-claim='%s' not found or non-textual in JWT; " +
        "falling back to sub-split. Set the claim or check your IdP token shape.",
        usernameClaim
      );
    }
    // Default: sub-split (last colon-separated segment).
    var splitted = subject.split(":");
    return splitted[splitted.length - 1];
  }

  /** Returns the configured username-claim name, or empty string if using sub-split default. */
  public String getUsernameClaim() {
    return usernameClaim;
  }

  /**
   * Walk the configured dot-path through the JWT body and collect any
   * string values found at the leaf. Tolerates a string array (Keycloak
   * shape), a single string, missing intermediate keys, or a non-array,
   * non-string leaf.
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
   * {@code :HAS_ROLE} grants. Today the only mapped role-string is the
   * configured {@code shepard.instance-admin.role}.
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
        Log.warnf("Failed to load Neo4j role grants for %s: %s", username, ex.getMessage());
      }
    }
    return out;
  }

  public static String[] parseClaimPath(String path) {
    if (path == null || path.isBlank()) return new String[0];
    String[] parts = path.split("\\.");
    return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }
}
