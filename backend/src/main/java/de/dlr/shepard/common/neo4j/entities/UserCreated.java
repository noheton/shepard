package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.auth.users.entities.User;

public interface UserCreated extends HasCreationDate {
  User getCreatedBy();
}
