package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.User;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserService {

  private UserDAO userDAO = new UserDAO();

  /**
   * Stores a new user in Neo4J.
   *
   * @param user The user to be stored.
   * @return The created user
   */
  public User createUser(User user) {
    log.info("Create user {}", user);
    return userDAO.createOrUpdate(user);
  }

  /**
   * Update a user in Neo4J. The user is created if it does not exist.
   *
   * @param user The user to be updated
   * @return The updated user
   */
  public User updateUser(User user) {
    User old = getUser(user.getUsername());
    if (old == null) {
      log.info("The user {} does not exist, creating...", user.getUsername());
      return userDAO.createOrUpdate(user);
    }

    String firstName = user.getFirstName() != null ? user.getFirstName() : old.getFirstName();
    String lastName = user.getLastName() != null ? user.getLastName() : old.getLastName();
    String email = user.getEmail() != null ? user.getEmail() : old.getEmail();

    if (!firstName.equals(old.getFirstName()) || !lastName.equals(old.getLastName()) || !email.equals(old.getEmail())) {
      old.setFirstName(firstName);
      old.setLastName(lastName);
      old.setEmail(email);
      log.info("Update user {}", old);
      return userDAO.createOrUpdate(old);
    }

    return old;
  }

  /**
   * Returns the user with the given name.
   *
   * @param username of the user to be returned.
   * @return The requested user.
   */
  public User getUser(String username) {
    return userDAO.find(username);
  }
}
