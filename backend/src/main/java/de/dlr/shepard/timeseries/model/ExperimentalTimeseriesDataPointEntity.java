package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "timeseries_payload")
public class ExperimentalTimeseriesDataPointEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @JsonIgnore
  @ManyToOne(targetEntity = ExperimentalTimeseriesEntity.class, fetch = FetchType.LAZY)
  @JoinColumn(name = "timeseries_id")
  private int timeseriesId;

  @Column(name = "time", nullable = false)
  private long time;

  @Column(name = "double_value", columnDefinition = "DOUBLE PRECISION", nullable = true)
  private Double doubleValue;

  @Column(name = "int_value", nullable = true)
  private Integer intValue;

  @Column(name = "string_value", columnDefinition = "TEXT", nullable = true)
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

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
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
