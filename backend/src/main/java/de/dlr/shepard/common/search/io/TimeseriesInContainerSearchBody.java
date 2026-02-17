package de.dlr.shepard.common.search.io;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TimeseriesInContainerSearchBody extends ASearchBody<AnnotatableTimeseriesInContainerSearchParams> {

  public TimeseriesInContainerSearchBody(AnnotatableTimeseriesInContainerSearchParams searchParams) {
    super(searchParams);
  }
}
