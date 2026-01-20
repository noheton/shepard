package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.context.semantic.HasAnnotation;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.inject.Inject;
import java.util.List;

public class TimeseriesWithAnnotations implements HasAnnotation {

  public int getTimeseriesId() {
    return tsSqlDb.getId();
  }

  public DataPointValueType getValueType() {
    return tsSqlDb.getValueType();
  }

  public String getMeasurement() {
    return tsSqlDb.getMeasurement();
  }

  public String getDevice() {
    return tsSqlDb.getDevice();
  }

  public String getLocation() {
    return tsSqlDb.getLocation();
  }

  public String getSymbolicName() {
    return tsSqlDb.getSymbolicName();
  }

  public String getField() {
    return tsSqlDb.getField();
  }

  public long getContainerId() {
    return tsGraphDb.getContainerId();
  }

  public List<SemanticAnnotation> getAnnotations() {
    return tsGraphDb.getAnnotations();
  }

  @Inject
  AnnotatableTimeseriesService annotationService;

  private final TimeseriesEntity tsSqlDb;
  private final AnnotatableTimeseries tsGraphDb;

  public TimeseriesWithAnnotations(TimeseriesEntity tsSqlDb) {
    this.tsGraphDb = new AnnotatableTimeseries(
      tsSqlDb.getContainerId(),
      tsSqlDb.getId(),
      annotationService.getAnnotations(tsSqlDb.getContainerId(), tsSqlDb.getId())
    );
    this.tsSqlDb = tsSqlDb;
  }
}
