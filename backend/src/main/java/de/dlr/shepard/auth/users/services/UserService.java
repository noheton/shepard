package de.dlr.shepard.auth.users.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RequestScoped
public class UserService {

  private static final ObjectMapper PREFS_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, String>> PREFS_TYPE = new TypeReference<>() {};

  @Inject
  UserDAO userDAO;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Update a user in Neo4J. The user is created if it does not exist.
   *
   * @param user The user to be updated
   * @return The updated user
   */
  public User createOrUpdateUser(User user) {
    Optional<User> oldUserOptional = getUserOptional(user.getUsername());
    if (oldUserOptional.isEmpty()) {
      // BUG-USER-PROVISION-EMAIL-COLLISION: when the Neo4j username constraint
      // would collide on email (stored node has a different username), fall back
      // to a lookup by email so the blind create doesn't throw
      // ConstraintValidationFailed and 500-storm every subsequent request.
      if (user.getEmail() != null && !user.getEmail().isBlank()) {
        Optional<User> byEmail = userDAO.findByEmail(user.getEmail());
        if (byEmail.isPresent()) {
          User existingNode = byEmail.get();
          Log.warnf(
            "BUG-USER-PROVISION-EMAIL-COLLISION: token username '%s' does not match stored " +
            "username '%s' for email '%s'. Adopting existing node without rename — ensure the " +
            "OIDC preferred_username claim is stable or set shepard.oidc.username-claim.",
            user.getUsername(),
            existingNode.getUsername(),
            user.getEmail()
          );
          return mergeProfileFields(existingNode, user);
        }
      }
      Log.infof("The user %s does not exist, creating...", user.getUsername());
      return userDAO.createOrUpdate(user);
    }
    User oldUser = oldUserOptional.get();
    return mergeProfileFields(oldUser, user);
  }

  private User mergeProfileFields(User target, User source) {
    String firstName = source.getFirstName() != null ? source.getFirstName() : target.getFirstName();
    String lastName = source.getLastName() != null ? source.getLastName() : target.getLastName();
    String email = source.getEmail() != null ? source.getEmail() : target.getEmail();

    if (
      !Objects.equals(firstName, target.getFirstName()) ||
      !Objects.equals(lastName, target.getLastName()) ||
      !Objects.equals(email, target.getEmail())
    ) {
      target.setFirstName(firstName);
      target.setLastName(lastName);
      target.setEmail(email);
      Log.infof("Update user %s", target);
      return userDAO.createOrUpdate(target);
    }

    return target;
  }

  /**
   * Returns the user with the given name.
   *
   * @param username of the user to be returned.
   * @return The requested user.
   * @throws InvalidPathException if the user does not exist
   */
  public User getUser(String username) {
    return getUserOptional(username).orElseThrow(() ->
      new InvalidPathException("User with name %s not found".formatted(username))
    );
  }

  /**
   * Returns the user with the given name if present
   *
   * @param username of the user to be returned
   * @return An optional containing the user if it exists
   */
  public Optional<User> getUserOptional(String username) {
    return Optional.ofNullable(userDAO.find(username));
  }

  /**
   * @return the user object for the user sending the request
   */
  public User getCurrentUser() {
    return resolveCurrentUser(false);
  }

  /**
   * Identity-only variant of {@link #getCurrentUser()} that loads the current
   * {@code :User} at depth 0 (no mapped collections).
   *
   * <p>NEO-AUDIT-2026-07-20-USER-SUPERNODE: {@link #getCurrentUser()} does a
   * {@code DEPTH_ENTITY=1} load that drags the shared service user's millions of
   * unmapped {@code WAS_ASSOCIATED_WITH} provenance edges over the wire on every
   * authenticated mutation (~3.3 s / ~hundreds of MB on the 2.87M-degree user, and
   * a JVM-heap risk under the ingest's 8× concurrency). Callers that only need the
   * user for a {@code setCreatedBy}/{@code setUpdatedBy} edge or a role check must use
   * this. Roles are scalar {@code @Property} fields so authorization is unaffected;
   * only the (here unused) mapped collections are skipped. See {@link UserDAO#findLight}.
   *
   * @return the current user at depth 0
   */
  public User getCurrentUserLight() {
    return resolveCurrentUser(true);
  }

  /**
   * Resolve the request's current user, optionally light (depth-0) to avoid the
   * {@code :User} supernode hydration. Shares the BUG-USER-PROVISION-EMAIL-COLLISION
   * email fallback so both variants behave identically on username divergence.
   */
  private User resolveCurrentUser(boolean light) {
    String username = authenticationContext.getCurrentUserName();
    User currentUser = light ? userDAO.findLight(username) : userDAO.find(username);

    if (currentUser == null) {
      // BUG-USER-PROVISION-EMAIL-COLLISION: when the stored Neo4j username diverges
      // from the token's preferred_username (e.g. importer service-account UUID vs.
      // interactive "admin"), the username lookup misses. Fall back to email if the
      // JWT carried the email claim, so the request can proceed with the correct node.
      String email = authenticationContext.getCurrentUserEmail();
      if (email != null && !email.isBlank()) {
        Optional<User> byEmail = userDAO.findByEmail(email);
        if (byEmail.isPresent()) {
          Log.warnf(
            "BUG-USER-PROVISION-EMAIL-COLLISION: getCurrentUser by username '%s' failed; " +
            "resolved via email '%s' to stored username '%s'.",
            authenticationContext.getCurrentUserName(),
            email,
            byEmail.get().getUsername()
          );
          return byEmail.get();
        }
      }
      String errorMsg = "Could not determine current user";
      Log.error(errorMsg);
      throw new InvalidRequestException(errorMsg);
    }
    return currentUser;
  }

  /**
   * @throws InvalidAuthException if the username does not match the user making the request
   *
   * <p>BUG-ASSERT-CURRENT-USER-EMAIL-FALLBACK: compares against the <em>resolved</em>
   * current user (the same robust {@link #getCurrentUser()} path that falls back to the
   * email claim), not the raw {@code principal.getUsername()}. When the stored Neo4j
   * username diverges from the token's {@code preferred_username} (e.g. a node keyed by
   * the OIDC {@code sub} while {@code shepard.oidc.username-claim=preferred_username}
   * yields {@code "admin"}), the raw-principal comparison deadlocked every
   * {@code assertCurrentUserEquals} caller — notably {@code POST /users/{username}/apikeys}:
   * {@code {username}=sub} → 403 here, {@code {username}=preferred_username} → 404 (no such
   * node). Resolving through {@link #getCurrentUser()} makes the self-scoped check pass
   * for the caller's own stored username exactly as the read paths already do. Guards
   * API-key mint, subscription writes, and every other self-scoped mutation.
   */
  public void assertCurrentUserEquals(String username) {
    User current = getCurrentUser();
    if (current == null || !current.getUsername().equals(username)) {
      throw new InvalidAuthException("The requested action is forbidden by the permission policies");
    }
  }

  /**
   * Returns the current user's UI preferences as a map.
   * When no preferences have been set (null {@code preferencesJson}),
   * returns an empty map. Per {@code aidocs/16 U1d}.
   *
   * @param username the user whose preferences to load
   * @return the preference map (never null; empty if none set)
   * @throws InvalidPathException if the user does not exist
   * @throws InvalidRequestException if stored JSON is malformed
   */
  public Map<String, String> getPreferences(String username) {
    User user = getUser(username);
    return parsePreferences(username, user.getPreferencesJson());
  }

  /**
   * Applies an RFC 7396 merge-patch to the current user's preferences.
   * Keys with non-null values are set; keys with null values are removed;
   * keys absent from the patch are preserved. Persists the merged map and
   * returns it. Per {@code aidocs/16 U1d}.
   *
   * @param username the user whose preferences to update
   * @param patch    the merge-patch to apply (null entry removes the key)
   * @return the resulting preference map after the patch is applied
   * @throws InvalidPathException if the user does not exist
   * @throws InvalidRequestException if the resulting map cannot be serialised
   */
  public Map<String, String> patchPreferences(String username, Map<String, String> patch) {
    User user = getUser(username);
    Map<String, String> current = parsePreferences(username, user.getPreferencesJson());

    for (Map.Entry<String, String> entry : patch.entrySet()) {
      if (entry.getValue() == null) {
        current.remove(entry.getKey());
      } else {
        current.put(entry.getKey(), entry.getValue());
      }
    }

    if (current.isEmpty()) {
      user.setPreferencesJson(null);
    } else {
      try {
        user.setPreferencesJson(PREFS_MAPPER.writeValueAsString(current));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Could not serialise preferences map: " + e.getMessage());
      }
    }
    userDAO.createOrUpdate(user);
    return current;
  }

  private Map<String, String> parsePreferences(String username, String json) {
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      return new HashMap<>(PREFS_MAPPER.readValue(json, PREFS_TYPE));
    } catch (JsonProcessingException e) {
      Log.warnf("Malformed preferencesJson for user %s: %s", username, e.getMessage());
      throw new InvalidRequestException("Stored preferences JSON is malformed for user: " + username);
    }
  }
}
