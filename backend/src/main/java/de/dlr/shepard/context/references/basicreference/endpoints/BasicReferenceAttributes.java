package de.dlr.shepard.context.references.basicreference.endpoints;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.util.List;

public enum BasicReferenceAttributes implements OrderByAttribute {
  createdAt,
  updatedAt,
  name,
  type;

  private static List<BasicReferenceAttributes> stringList = List.of(
    BasicReferenceAttributes.name,
    BasicReferenceAttributes.type
  );

  private boolean isString(BasicReferenceAttributes referenceAttribute) {
    return stringList.contains(referenceAttribute);
  }

  @Override
  public boolean isString() {
    return isString(this);
  }
}
