package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TimeseriesWithAnnotationsFactoryService {

  @Inject
  AnnotatableTimeseriesService annotationService;

  @Inject
  TimeseriesService timeseriesService;

  public TimeseriesWithAnnotations create(TimeseriesEntity timeseriesEntity) {
    AnnotatableTimeseries annotatableTimeseries = new AnnotatableTimeseries(
      timeseriesEntity.getContainerId(),
      timeseriesEntity.getId(),
      annotationService.getAnnotations(timeseriesEntity.getContainerId(), timeseriesEntity.getId())
    );
    return new TimeseriesWithAnnotations(timeseriesEntity, annotatableTimeseries);
  }

  public TimeseriesWithAnnotations create(AnnotatableTimeseries annotatableTimeseries) {
    return new TimeseriesWithAnnotations(
      timeseriesService.getTimeseriesById(
        annotatableTimeseries.getContainerId(),
        annotatableTimeseries.getTimeseriesId()
      ),
      annotatableTimeseries
    );
  }
}
