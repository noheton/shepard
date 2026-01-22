package de.dlr.shepard.common.search.io;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.data.timeseries.io.TimeseriesIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesWithAnnotations;
import de.dlr.shepard.data.timeseries.model.TimeseriesWithAnnotationsFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TimeseriesInContainerSearchResult extends ASearchResults<AnnotatableTimeseriesInContainerSearchParams> {

  private List<TimeseriesIO> results;

  public TimeseriesInContainerSearchResult(List<AnnotatableTimeseries> resultList) {
    TimeseriesWithAnnotationsFactory factory = new TimeseriesWithAnnotationsFactory();
    /*results = resultList
      .stream()
      .map(TimeseriesWithAnnotations::new)
      .map(TimeseriesIO::new)
      .collect(Collectors.toList());*/
    results = new ArrayList<TimeseriesIO>();
    for (AnnotatableTimeseries annotatableTimeseries : resultList) {
      TimeseriesWithAnnotations timeseriesWithAnnotations = factory.createTSWithAnno(annotatableTimeseries);
      TimeseriesIO timeseriesIO = new TimeseriesIO(timeseriesWithAnnotations);
      results.add(timeseriesIO);
    }
  }
}
