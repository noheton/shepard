package de.dlr.shepard.data.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
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
public class TimeseriesEntity {

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
  private DataPointValueType valueType;

  public TimeseriesEntity() {}

  public TimeseriesEntity(
    long containerId,
    String measurement,
    String field,
    String device,
    String location,
    String symbolicName,
    DataPointValueType valueType
  ) {
    this.containerId = containerId;
    this.measurement = measurement;
    this.field = field;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.valueType = valueType;
  }

  public TimeseriesEntity(long containerId, Timeseries timeseries, DataPointValueType valueType) {
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

  public DataPointValueType getValueType() {
    return valueType;
  }

  @JsonIgnore
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field, valueType.toString());
  }
}
