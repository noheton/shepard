package de.dlr.shepard.common.search.io;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AnnotatableTimeseriesInContainerSearchParams extends ASearchParams {

  @Valid
  @NotNull
  private Long containerId;

  public AnnotatableTimeseriesInContainerSearchParams(String query, Long containerId) {
    super(query);
    this.containerId = containerId;
  }
}
