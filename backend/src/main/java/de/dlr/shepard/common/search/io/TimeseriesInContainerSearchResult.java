package de.dlr.shepard.common.search.io;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.io.AnnotatableTimeseriesIO;
import java.util.List;
import lombok.Data;

@Data
public class TimeseriesInContainerSearchResult extends ASearchResults<AnnotatableTimeseriesInContainerSearchParams> {

  private AnnotatableTimeseriesIO[] results;

  public TimeseriesInContainerSearchResult(List<AnnotatableTimeseries> resultList) {
    results = new AnnotatableTimeseriesIO[resultList.size()];
    for (int i = 0; i < resultList.size(); i++) results[i] = new AnnotatableTimeseriesIO(resultList.get(i));
  }
}
