package de.dlr.shepard.common.search.endpoints;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum BasicCollectionAttributes implements OrderByAttribute {
  updatedAt,
  createdAt,
  createdBy,
  id,
  name;

  private static List<BasicCollectionAttributes> stringList = List.of(
    BasicCollectionAttributes.name,
    BasicCollectionAttributes.createdBy
  );

  private boolean isString(BasicCollectionAttributes dataObjectAttribute) {
    return stringList.contains(dataObjectAttribute);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
