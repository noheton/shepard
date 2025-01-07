package de.dlr.shepard.auth.users.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class UserDAO extends GenericDAO<User> {

  public User find(String username) {
    User entity = session.load(getEntityType(), username, DEPTH_ENTITY);
    return entity;
  }

  public boolean delete(String username) {
    User entity = session.load(getEntityType(), username);
    if (entity != null) {
      session.delete(entity);
      return true;
    }
    return false;
  }

  @Override
  public Class<User> getEntityType() {
    return User.class;
  }
}
