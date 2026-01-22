package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.inject.Inject;

public class TimeseriesWithAnnotationsFactory {

  @Inject
  TimeseriesService timeseriesService;

  public TimeseriesWithAnnotationsFactory() {}

  public TimeseriesWithAnnotations createTSWithAnno(AnnotatableTimeseries tsGraphDb) {
    TimeseriesEntity entity = timeseriesService.getTimeseriesById(2l, 1);
    return timeseriesService.createTSWithAnno(tsGraphDb);
  }
}
