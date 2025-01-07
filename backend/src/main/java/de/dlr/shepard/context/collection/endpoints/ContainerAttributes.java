package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum ContainerAttributes implements OrderByAttribute {
  createdAt,
  updatedAt,
  name;

  private static List<ContainerAttributes> stringList = List.of(ContainerAttributes.name);

  private boolean isString(ContainerAttributes containerAttribute) {
    return stringList.contains(containerAttribute);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
