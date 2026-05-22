package de.dlr.shepard.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.common.util.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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

  @Inject
  RoleDAO roleDAO;

  /** Required by the CDI runtime for {@code @ApplicationScoped} proxy generation. */
  protected JwtTokenAuthService() {
    this.oidcPublicKey = null;
    this.requiredOidcRole = "";
    this.rolesClaimPath = new String[0];
    this.instanceAdminClaimValue = "";
  }

  @Inject
  public JwtTokenAuthService(
    @ConfigProperty(name = "oidc.public") String oidcPublic,
    @ConfigProperty(name = "oidc.role") Optional<String> oidcRole,
    @ConfigProperty(
      name = "shepard.oidc.roles-claim-path",
      defaultValue = "realm_access.roles"
    ) String rolesClaimPath,
    @ConfigProperty(name = "shepard.instance-admin.role") Optional<String> instanceAdminClaimValue
  ) throws NoSuchAlgorithmException, InvalidKeySpecException {
    this.requiredOidcRole = oidcRole.orElse("");
    this.rolesClaimPath = parseClaimPath(rolesClaimPath);
    this.instanceAdminClaimValue = instanceAdminClaimValue.map(String::trim).orElse("");

    var kFactory = KeyFactory.getInstance("RSA");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(oidcPublic);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The given oidc public key is invalid", e);
    }
    this.oidcPublicKey = kFactory.generatePublic(new X509EncodedKeySpec(decoded));
  }

  /** Test-only constructor that lets unit tests inject a pre-built public key. */
  JwtTokenAuthService(
    PublicKey oidcPublicKey,
    String requiredOidcRole,
    String[] rolesClaimPath,
    String instanceAdminClaimValue,
    RoleDAO roleDAO
  ) {
    this.oidcPublicKey = oidcPublicKey;
    this.requiredOidcRole = requiredOidcRole == null ? "" : requiredOidcRole;
    this.rolesClaimPath = rolesClaimPath == null ? new String[0] : rolesClaimPath;
    this.instanceAdminClaimValue =
      instanceAdminClaimValue == null ? "" : instanceAdminClaimValue.trim();
    this.roleDAO = roleDAO;
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

    var splitted = subject.split(":");
    String username = splitted[splitted.length - 1];

    Set<String> resolvedRoles = resolveDualSourceRoles(username, idpRoles);

    java.util.Date issuedAt = body.getIssuedAt();
    long iat = issuedAt != null ? issuedAt.getTime() / 1000L : 0L;

    return new JWTPrincipal(audience, issuedFor, username, keyId, resolvedRoles, iat);
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
