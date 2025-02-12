package de.dlr.shepard.common.util;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class SortingHelper {

  private OrderByAttribute orderByAttribute;
  private Boolean orderDesc;

  public boolean hasOrderByAttribute() {
    return this.orderByAttribute != null;
  }
}
