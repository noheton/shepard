package de.dlr.shepard.data.spatialdata.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "FilterCondition", description = "A single attribute-based filter condition (key, operator, value) for spatial data queries.")
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
