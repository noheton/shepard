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
      Log.infof("The user %s does not exist, creating...", user.getUsername());
      return userDAO.createOrUpdate(user);
    }
    User oldUser = oldUserOptional.get();

    String firstName = user.getFirstName() != null ? user.getFirstName() : oldUser.getFirstName();
    String lastName = user.getLastName() != null ? user.getLastName() : oldUser.getLastName();
    String email = user.getEmail() != null ? user.getEmail() : oldUser.getEmail();
    // U1g — ORCID "fill if missing" semantics. If the user manually
    // PATCHed their ORCID via /v2/users/me the value stays put across
    // logins; if the local value is null and the IdP supplies one,
    // adopt it on next login. (Same posture as the rest of this block
    // for firstName/lastName/email: IdP wins only when local is null,
    // which the ternaries above enforce via != null fallback.)
    String orcid = oldUser.getOrcid() != null ? oldUser.getOrcid() : user.getOrcid();

    if (
      !firstName.equals(oldUser.getFirstName()) ||
      !lastName.equals(oldUser.getLastName()) ||
      !email.equals(oldUser.getEmail()) ||
      !java.util.Objects.equals(orcid, oldUser.getOrcid())
    ) {
      oldUser.setFirstName(firstName);
      oldUser.setLastName(lastName);
      oldUser.setEmail(email);
      oldUser.setOrcid(orcid);
      Log.infof("Update user %s", oldUser);
      return userDAO.createOrUpdate(oldUser);
    }

    return oldUser;
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
    User currentUser = userDAO.find(authenticationContext.getCurrentUserName());

    if (currentUser == null) {
      String errorMsg = "Could not determine current user";
      Log.error(errorMsg);
      throw new InvalidRequestException(errorMsg);
    }
    return currentUser;
  }

  /**
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public void assertCurrentUserEquals(String username) {
    if (!authenticationContext.getCurrentUserName().equals(username)) {
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
