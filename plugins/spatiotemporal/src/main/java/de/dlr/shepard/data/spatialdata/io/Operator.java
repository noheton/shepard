package de.dlr.shepard.data.spatialdata.io;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Operator", description = "Comparison operator used in a FilterCondition for spatial data queries.")
@Getter
@NoArgsConstructor
public enum Operator {
  EQUALS("="),
  GREATER_THAN(">"),
  LESS_THAN("<");

  private String operatorString;

  Operator(String operatorString) {
    this.operatorString = operatorString;
  }
}
