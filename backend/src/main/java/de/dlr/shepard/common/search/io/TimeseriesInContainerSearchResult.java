package de.dlr.shepard.common.search.io;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesWithAnnotationsFactoryService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class TimeseriesInContainerSearchResult extends ASearchResults<AnnotatableTimeseriesInContainerSearchParams> {

  private List<TimeseriesIO> results;

  public TimeseriesInContainerSearchResult(
    TimeseriesWithAnnotationsFactoryService ts,
    List<AnnotatableTimeseries> resultList
  ) {
    results = resultList.stream().map(ts::create).map(TimeseriesIO::new).collect(Collectors.toList());
  }
}
