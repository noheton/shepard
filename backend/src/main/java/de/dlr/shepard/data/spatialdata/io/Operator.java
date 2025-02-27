package de.dlr.shepard.data.spatialdata.io;

import lombok.Getter;
import lombok.NoArgsConstructor;

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
