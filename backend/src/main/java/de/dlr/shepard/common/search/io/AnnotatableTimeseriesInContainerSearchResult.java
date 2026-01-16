package de.dlr.shepard.common.search.io;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.io.AnnotatableTimeseriesIO;
import java.util.List;

public class AnnotatableTimeseriesInContainerSearchResult
  extends ASearchResults<AnnotatableTimeseriesInContainerSearchParams> {

  private AnnotatableTimeseriesIO[] results;

  public AnnotatableTimeseriesInContainerSearchResult(List<AnnotatableTimeseries> resultList) {
    results = new AnnotatableTimeseriesIO[resultList.size()];
    for (int i = 0; i < resultList.size(); i++) results[i] = new AnnotatableTimeseriesIO(resultList.get(i));
  }
}
