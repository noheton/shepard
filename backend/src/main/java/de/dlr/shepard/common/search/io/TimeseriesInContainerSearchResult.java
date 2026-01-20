package de.dlr.shepard.common.search.io;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesWithAnnotations;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class TimeseriesInContainerSearchResult extends ASearchResults<AnnotatableTimeseriesInContainerSearchParams> {

  private List<TimeseriesIO> results;

  public TimeseriesInContainerSearchResult(List<AnnotatableTimeseries> resultList) {
    results = resultList
      .stream()
      .map(TimeseriesWithAnnotations::new)
      .map(TimeseriesIO::new)
      .collect(Collectors.toList());
  }
}
