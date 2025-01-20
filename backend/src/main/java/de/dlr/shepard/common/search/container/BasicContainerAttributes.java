package de.dlr.shepard.common.search.container;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum BasicContainerAttributes implements OrderByAttribute {
  createdAt,
  createdBy,
  containerType,
  id,
  name;

  private static List<BasicContainerAttributes> stringList = List.of(
    BasicContainerAttributes.name,
    BasicContainerAttributes.containerType,
    BasicContainerAttributes.createdBy
  );

  private boolean isString(BasicContainerAttributes dataObjectAttribute) {
    return stringList.contains(dataObjectAttribute);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
