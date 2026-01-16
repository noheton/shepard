package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AnnotatableTimeseriesInContainerSearchBody
  extends ASearchBody<AnnotatableTimeseriesInContainerSearchParams> {

  public AnnotatableTimeseriesInContainerSearchBody(AnnotatableTimeseriesInContainerSearchParams searchParams) {
    super(searchParams);
  }
}
