package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.User;
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
