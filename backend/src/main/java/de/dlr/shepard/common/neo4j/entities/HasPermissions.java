package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.auth.permission.model.Permissions;

public interface HasPermissions {
  Permissions getPermissions();
  void setPermissions(Permissions permissions);
}
