package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.context.semantic.HasAnnotation;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.List;

public class TimeseriesWithAnnotations implements HasAnnotation {

  private final TimeseriesEntity tsSqlDb;
  private final AnnotatableTimeseries tsGraphDb;

  public int getTimeseriesId() {
    return tsGraphDb.getTimeseriesId();
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

  public TimeseriesWithAnnotations(TimeseriesEntity tsSqlDb, AnnotatableTimeseries tsGraphDb) {
    this.tsSqlDb = tsSqlDb;
    this.tsGraphDb = tsGraphDb;
  }
}
