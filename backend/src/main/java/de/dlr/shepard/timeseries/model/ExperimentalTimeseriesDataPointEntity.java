package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "timeseries_payload")
public class ExperimentalTimeseriesDataPointEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @JsonIgnore
  @Column(name = "timeseries_id", nullable = false)
  private int timeseriesId;

  @Column(name = "time", nullable = false)
  private LocalDateTime time;

  @Column(name = "double_value", nullable = true)
  private Double doubleValue;

  @Column(name = "int_value", nullable = true)
  private Integer intValue;

  @Column(name = "string_value", nullable = true)
  private String stringValue;

  @Column(name = "boolean_value", nullable = true)
  private Boolean booleanValue;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public Double getDoubleValue() {
    return doubleValue;
  }

  public void setDoubleValue(Double floatValue) {
    this.doubleValue = floatValue;
  }

  public Integer getIntValue() {
    return intValue;
  }

  public void setIntValue(Integer intValue) {
    this.intValue = intValue;
  }

  public String getStringValue() {
    return stringValue;
  }

  public void setStringValue(String stringValue) {
    this.stringValue = stringValue;
  }

  public Boolean getBooleanValue() {
    return booleanValue;
  }

  public void setBooleanValue(Boolean booleanValue) {
    this.booleanValue = booleanValue;
  }

  public int getTimeseriesId() {
    return timeseriesId;
  }

  public void setTimeseriesId(int timeseriesId) {
    this.timeseriesId = timeseriesId;
  }

  public LocalDateTime getTime() {
    return time;
  }

  public void setTime(LocalDateTime time) {
    this.time = time;
  }

  public String toString() {
    return String.format(
      "id=%s timeseriesId=%s time=%s doubleValue=%f intValue=%d stringValue=%s booleanValue=%b",
      id,
      timeseriesId,
      time,
      doubleValue,
      intValue,
      stringValue,
      booleanValue
    );
  }
}
