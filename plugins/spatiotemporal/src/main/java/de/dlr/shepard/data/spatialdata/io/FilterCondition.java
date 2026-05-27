package de.dlr.shepard.data.spatialdata.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FilterCondition {

  public FilterCondition() {}

  public FilterCondition(@NotNull String key, @NotNull Operator operator, @NotNull double value) {
    this.key = key;
    this.operator = operator;
    this.value = value;
  }

  @NotNull
  private String key;

  @NotNull
  private Operator operator;

  @NotNull
  private double value;
}
