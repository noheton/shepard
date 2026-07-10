package de.dlr.shepard.auth.apikey.services;

import de.dlr.shepard.auth.apikey.daos.ApiKeyDAO;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.auth.role.daos.RoleDAO;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@RequestScoped
public class ApiKeyService {

  @Inject
  ApiKeyDAO apiKeyDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  PKIHelper pkiHelper;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  RoleDAO roleDAO;

  /**
   * A0 §4.2 — operator-configurable allowlist of roles that API keys
   * may carry. Default includes every realm role shepard ships
   * (`instance-admin`, `quality-engineer`); shrink to empty (set the
   * property to nothing) to forbid role-bearing keys entirely. Modelled
   * as {@code Optional<String[]>} so SmallRye config doesn't reject
   * empty values.
   */
  @Inject
  @ConfigProperty(
    name = "shepard.apikey.role-allowlist",
    defaultValue = Constants.INSTANCE_ADMIN_ROLE + "," + Constants.QUALITY_ENGINEER_ROLE
  )
  java.util.Optional<String[]> apiKeyRoleAllowlistOpt;

  /**
   * Searches the neo4j database for all ApiKeys associated with a given user.
   *
   * @param username Identifies the associated user
   * @return The list of ApiKeys associated with the given user
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public List<ApiKey> getAllApiKeys(String username) {
    userService.assertCurrentUserEquals(username);
    User user = userService.getUser(username);
    return user.getApiKeys();
  }

  /**
   * Searches the neo4j database for an ApiKey
   *
   * @param username of the user owning the api key
   * @param apiKeyUid Identifies the ApiKey to be searched
   * @return The ApiKey with the given uid or null
   * @throws InvalidPathException if the ApiKey or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the ApiKey does not belong to the user
   */
  public ApiKey getApiKey(String username, UUID apiKeyUid) {
    userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    ApiKey requestedKey = apiKeyDAO.find(apiKeyUid);

    if (requestedKey == null) {
      throw new InvalidPathException("ID ERROR - ApiKey does not exist");
    }
    if (!requestedKey.getBelongsTo().getUsername().equals(username)) {
      throw new InvalidAuthException("You do not have permissions for this ApiKey.");
    }

    return apiKeyDAO.find(apiKeyUid);
  }

  /**
   * Searches the neo4j database for an ApiKey.
   *
   * @param apiKeyUid Identifies the ApiKey to be searched
   * @return The ApiKey with the given uid or null
   */
  public ApiKey getApiKey(UUID apiKeyUid) {
    ApiKey requestedKey = apiKeyDAO.find(apiKeyUid);

    if (requestedKey == null) {
      throw new InvalidPathException("ID ERROR - ApiKey does not exist");
    }

    return requestedKey;
  }

  /**
   * Creates an ApiKey and stores it in neo4j
   *
   * @param apiKey   The ApiKey to be stored
   * @param username The user who wants to create an apiKey
   * @param baseUri  The current Uri
   * @return The created ApiKey
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public ApiKey createApiKey(ApiKeyIO apiKey, String username, String baseUri) {
    var user = userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    var now = dateHelper.getDate();
    var validUntil = apiKey.getValidUntil();
    if (validUntil != null && !validUntil.after(now)) {
      throw new InvalidRequestException("validUntil must be in the future");
    }

    Set<String> requestedRoles = apiKey.getRoles() == null ? Set.of() : new HashSet<>(apiKey.getRoles());
    validateRequestedRoles(requestedRoles, username);

    // RESEED-FIND-APIKEY-ROLES — a minted API key must carry the same
    // role set the interactive OIDC token would, so `@RolesAllowed`
    // checks (NCR `status` needs `quality-engineer`, admin surfaces need
    // `instance-admin`) pass for API-key auth exactly as they do for an
    // interactive token. When the caller doesn't explicitly narrow the
    // role set, default to the user's *effective* roles (the same source
    // JwtTokenAuthService.resolveDualSourceRoles reads — the Neo4j
    // `:HAS_ROLE` grants) intersected with the operator allowlist. When
    // the caller asks for an explicit subset, honour that (already
    // validated above for allowlist membership + caller-must-hold).
    Set<String> effectiveRoles = requestedRoles.isEmpty()
      ? resolveEffectiveRoles(username)
      : requestedRoles;

    var toCreate = new ApiKey();
    toCreate.setBelongsTo(user);
    toCreate.setCreatedAt(now);
    toCreate.setName(apiKey.getName());
    toCreate.setValidUntil(validUntil);
    toCreate.setRoles(effectiveRoles);

    var createdApiKey = apiKeyDAO.createOrUpdate(toCreate);
    createdApiKey.setJws(generateJws(createdApiKey, baseUri));
    return apiKeyDAO.createOrUpdate(createdApiKey);
  }

  /**
   * A0 §4.2 — verify the requested role-set is valid for the caller:
   *
   * <ol>
   *   <li>Each role must be in {@code shepard.apikey.role-allowlist}
   *       (operator-configurable; default {@code ["instance-admin"]}).
   *   <li>Each role must already be held by the caller — looked up via
   *       both the JWT principal's deduped roles AND the Neo4j edge
   *       (the dual-source check). No privilege escalation: a non-admin
   *       can't mint admin keys.
   * </ol>
   *
   * <p>Empty role set (the default) skips both checks — preserves
   * today's behaviour for existing API-key flows.
   */
  void validateRequestedRoles(Set<String> requestedRoles, String username) {
    if (requestedRoles == null || requestedRoles.isEmpty()) return;

    Set<String> allowlist = parseAllowlist();

    Set<String> callerRoles = new HashSet<>();
    var principal = authenticationContext == null ? null : authenticationContext.getPrincipal();
    if (principal != null && principal.getRoles() != null) {
      callerRoles.addAll(Arrays.asList(principal.getRoles()));
    }
    // Belt-and-braces: also consult the Neo4j edge directly, so a
    // mint requested via a fresh request whose principal hasn't yet
    // been dual-source-resolved still works.
    if (roleDAO != null) {
      try {
        callerRoles.addAll(roleDAO.rolesForUser(username));
      } catch (RuntimeException ignored) {
        // best-effort, see JWTFilter notes
      }
    }

    for (String role : requestedRoles) {
      if (role == null || role.isBlank()) continue;
      if (!allowlist.contains(role)) {
        throw new InvalidRequestException(
          "Role '" + role + "' is not in shepard.apikey.role-allowlist=" + allowlist
        );
      }
      if (!callerRoles.contains(role)) {
        throw new InvalidAuthException(
          "Caller does not hold role '" + role + "' — cannot mint an API key with it."
        );
      }
    }
  }

  /**
   * RESEED-FIND-APIKEY-ROLES — resolve the user's effective realm roles
   * for the default (no explicit-roles-requested) mint path, bounded by
   * the operator allowlist. Reads the same Neo4j {@code :HAS_ROLE}
   * grants that {@code JwtTokenAuthService.resolveDualSourceRoles} reads
   * for the interactive token, so the minted key and the interactive
   * token resolve from ONE source of truth. The allowlist intersection
   * preserves the operator's ability to forbid role-bearing keys
   * entirely (empty allowlist → empty role set, today's behaviour).
   */
  Set<String> resolveEffectiveRoles(String username) {
    Set<String> allowlist = parseAllowlist();
    if (allowlist.isEmpty()) return Set.of();

    Set<String> effective = new HashSet<>();
    if (roleDAO != null) {
      try {
        effective.addAll(roleDAO.rolesForUser(username));
      } catch (RuntimeException ignored) {
        // best-effort, see JWTFilter notes — degrade to no roles rather
        // than failing the mint on a transient Neo4j read.
      }
    }
    effective.retainAll(allowlist);
    return effective;
  }

  /**
   * Parse {@code shepard.apikey.role-allowlist} into a trimmed,
   * blank-free set. Shared by the explicit-request validation and the
   * default effective-role resolution.
   */
  private Set<String> parseAllowlist() {
    Set<String> allowlist = new HashSet<>();
    String[] configured = apiKeyRoleAllowlistOpt == null ? null : apiKeyRoleAllowlistOpt.orElse(new String[0]);
    if (configured != null) {
      for (String r : configured) {
        if (r != null && !r.isBlank()) allowlist.add(r.trim());
      }
    }
    return allowlist;
  }

  /**
   * Deletes an ApiKey from neo4j
   *
   * @param apiKeyUid Identifies the ApiKey to be deleted
   * @return A boolean to identify whether the ApiKey was successfully removed
   * @throws InvalidPathException if the ApiKey or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the ApiKey does not belong to the user
   */
  public boolean deleteApiKey(String username, UUID apiKeyUid) {
    userService.assertCurrentUserEquals(username);
    getApiKey(username, apiKeyUid);

    return apiKeyDAO.delete(apiKeyUid);
  }

  /**
   * Generates and sets a signed JSON Web Token for the given ApiKey object by
   * using an RSA-Key and the following attributes: username as the JWT claim
   * "subject", the URL of this backend software as the JWT claim "issuer", the id
   * of the apiKey as the JWT claim "id" and the current date for the JWT claims
   * "not before" and "issued at".
   *
   * @param apiKey  The apiKey for which the JSON Web Token should be generated.
   * @param baseUri Contains the context of the request in order to set JWT claim
   *                "issuer"
   */
  private String generateJws(ApiKey apiKey, String baseUri) {
    pkiHelper.init();
    var currentDate = dateHelper.getDate();
    var builder = Jwts.builder()
      .subject(apiKey.getBelongsTo().getUsername())
      .issuer(baseUri)
      .notBefore(currentDate)
      .issuedAt(currentDate)
      .id(apiKey.getUid().toString());
    if (apiKey.getValidUntil() != null) {
      builder.expiration(apiKey.getValidUntil());
    }
    Set<String> roles = apiKey.getRoles();
    if (roles != null && !roles.isEmpty()) {
      // Sorted list for deterministic token shape.
      var sorted = new java.util.TreeSet<>(roles);
      builder.claim(Constants.ROLES, java.util.List.copyOf(sorted));
    }
    return builder.signWith(pkiHelper.getPrivateKey()).compact();
  }

  // Ignore Sonar — referenced in unit tests for visibility into the empty-by-default state.
  Set<String> emptyRoles() {
    return Collections.emptySet();
  }
}
