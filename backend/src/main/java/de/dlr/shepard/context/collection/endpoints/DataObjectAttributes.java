package de.dlr.shepard.context.collection.endpoints;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum DataObjectAttributes implements OrderByAttribute {
  createdAt,
  updatedAt,
  name;

  private static List<DataObjectAttributes> stringList = List.of(DataObjectAttributes.name);

  private boolean isString(DataObjectAttributes dataObjectAttribute) {
    return stringList.contains(dataObjectAttribute);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
