package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.auth.users.entities.User;
import java.util.Date;

public interface UserUpdatable {
  Date getUpdatedAt();
  User getUpdatedBy();
}
