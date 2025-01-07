package de.dlr.shepard.auth.users.endpoints;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum UserGroupAttributes implements OrderByAttribute {
  createdAt,
  updatedAt,
  name;

  private static List<UserGroupAttributes> stringList = List.of(UserGroupAttributes.name);

  private boolean isString(UserGroupAttributes userGroupAttributes) {
    return stringList.contains(userGroupAttributes);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
