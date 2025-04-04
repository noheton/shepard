package de.dlr.shepard.auth.users.services;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@RequestScoped
public class UserService {

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

    if (
      !firstName.equals(oldUser.getFirstName()) ||
      !lastName.equals(oldUser.getLastName()) ||
      !email.equals(oldUser.getEmail())
    ) {
      oldUser.setFirstName(firstName);
      oldUser.setLastName(lastName);
      oldUser.setEmail(email);
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
      new InvalidPathException(String.format("User with name %s not found", username))
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
}
