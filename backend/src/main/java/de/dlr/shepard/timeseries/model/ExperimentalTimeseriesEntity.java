package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@NoArgsConstructor
@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseriesEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "container_id")
  private long containerId;

  @NotBlank
  private String measurement;

  @NotBlank
  private String field;

  @NotBlank
  @Schema(nullable = true)
  private String device;

  @NotBlank
  @Schema(nullable = true)
  private String location;

  @NotBlank
  @Schema(nullable = true)
  @Column(name = "symbolic_name")
  private String symbolicName;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type")
  private ExperimentalDataPointValueTypes valueType;

  public ExperimentalTimeseriesEntity(
    long containerId,
    @NotBlank String measurement,
    @NotBlank String field,
    @NotBlank String device,
    @NotBlank String location,
    @NotBlank String symbolicName,
    @NotBlank ExperimentalDataPointValueTypes valueType
  ) {
    this.containerId = containerId;
    this.measurement = measurement;
    this.field = field;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.valueType = valueType;
  }

  public ExperimentalTimeseriesEntity(
    long containerId,
    ExperimentalTimeseries timeseries,
    ExperimentalDataPointValueTypes valueType
  ) {
    this(
      containerId,
      timeseries.getMeasurement(),
      timeseries.getField(),
      timeseries.getDevice(),
      timeseries.getLocation(),
      timeseries.getSymbolicName(),
      valueType
    );
  }

  public int getId() {
    return id;
  }

  public String getMeasurement() {
    return measurement;
  }

  public long getContainerId() {
    return containerId;
  }

  public String getDevice() {
    return device;
  }

  public String getLocation() {
    return location;
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  public String getField() {
    return field;
  }

  public ExperimentalDataPointValueTypes getValueType() {
    return valueType;
  }

  public void setValueType(ExperimentalDataPointValueTypes valueType) {
    this.valueType = valueType;
  }

  @JsonIgnore
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field, valueType.toString());
  }
}
