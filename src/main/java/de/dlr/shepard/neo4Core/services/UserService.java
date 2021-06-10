package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.security.JWTPrincipal;
import lombok.extern.log4j.Log4j2;

@Log4j2
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
	 * Update a user in Neo4J with data from JWTPrincipal. The user is created if it
	 * does not exist.
	 * 
	 * @param principal The JWTPrincipal to be updated
	 * @return The updated user
	 */
	public User updateUser(JWTPrincipal principal) {
		User user = getUser(principal.getUsername());
		if (user == null) {
			log.info("The user {} does not exist, creating...", principal.getUsername());
			user = convertPrincipal(principal);
			return userDAO.createOrUpdate(user);
		}

		String firstName = principal.getFirstName() != null ? principal.getFirstName() : user.getFirstName();
		String lastName = principal.getLastName() != null ? principal.getLastName() : user.getLastName();
		String email = principal.getEmail() != null ? principal.getEmail() : user.getEmail();

		if (!firstName.equals(user.getFirstName()) || !lastName.equals(user.getLastName())
				|| !email.equals(user.getEmail())) {
			user.setFirstName(firstName);
			user.setLastName(lastName);
			user.setEmail(email);
			log.info("Update user {}", user);
			return userDAO.createOrUpdate(user);
		}

		return user;
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

	private User convertPrincipal(JWTPrincipal principal) {
		User user = new User(principal.getUsername(), principal.getFirstName(), principal.getLastName(),
				principal.getEmail());

		return user;
	}
}
