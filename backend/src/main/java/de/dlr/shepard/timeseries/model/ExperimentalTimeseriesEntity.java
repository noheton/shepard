package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.timeseries.model.enums.ExperimentalDataPointValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseriesEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "container_id", nullable = false)
  private long containerId;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String measurement;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String field;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String device;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String location;

  @Column(name = "symbolic_name", columnDefinition = "TEXT", nullable = false)
  private String symbolicName;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", columnDefinition = "TEXT", nullable = false)
  private ExperimentalDataPointValueType valueType;

  public ExperimentalTimeseriesEntity() {}

  public ExperimentalTimeseriesEntity(
    long containerId,
    String measurement,
    String field,
    String device,
    String location,
    String symbolicName,
    ExperimentalDataPointValueType valueType
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
    ExperimentalDataPointValueType valueType
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

  public ExperimentalDataPointValueType getValueType() {
    return valueType;
  }

  @JsonIgnore
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field, valueType.toString());
  }
}
