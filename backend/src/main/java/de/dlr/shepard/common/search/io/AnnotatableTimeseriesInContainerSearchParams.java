package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AnnotatableTimeseriesInContainerSearchParams extends ASearchParams {

  public AnnotatableTimeseriesInContainerSearchParams(String query) {
    super(query);
  }
}
