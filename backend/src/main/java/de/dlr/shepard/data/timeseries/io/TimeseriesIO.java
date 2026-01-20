package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.TimeseriesWithAnnotations;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TimeseriesEntity")
@Data
public class TimeseriesIO {

  @NotBlank
  private final int id;

  @NotBlank
  private final long containerId;

  @NotBlank
  private final DataPointValueType valueType;

  @NotBlank
  private final String measurement;

  @NotBlank
  private final String device;

  @NotBlank
  private final String location;

  @NotBlank
  private final String symbolicName;

  @NotBlank
  private final String field;

  @NotBlank
  private int timeseriesId;

  List<SemanticAnnotation> annotationIds;

  public TimeseriesIO(TimeseriesWithAnnotations timeseries) {
    this.id = timeseries.getTimeseriesId();
    this.containerId = timeseries.getContainerId();
    this.valueType = timeseries.getValueType();
    this.measurement = timeseries.getMeasurement();
    this.device = timeseries.getDevice();
    this.location = timeseries.getLocation();
    this.symbolicName = timeseries.getSymbolicName();
    this.field = timeseries.getField();
    this.timeseriesId = timeseries.getTimeseriesId();
    this.annotationIds = timeseries.getAnnotations();
  }
}
