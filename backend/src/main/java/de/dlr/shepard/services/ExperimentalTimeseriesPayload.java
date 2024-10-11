package de.dlr.shepard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "timeseries_payload")
public class ExperimentalTimeseriesPayload {

  //Not used but here to satisfy the JPA specification to have a unique id attribute.
  @Id
  private Long id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "timeseries_id", nullable = false)
  private ExperimentalTimeseries timeseries;

  @Column(name = "time", nullable = false)
  private LocalDateTime time;

  @Column(name = "float_value")
  private Double floatValue;

  @Column(name = "int_value")
  private Integer intValue;

  @Column(name = "string_value")
  private String stringValue;

  @Column(name = "boolean_value")
  private Boolean booleanValue;

  public Double getFloatValue() {
    return floatValue;
  }

  public void setFloatValue(Double floatValue) {
    this.floatValue = floatValue;
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

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ExperimentalTimeseries getTimeseries() {
    return timeseries;
  }

  public void setTimeseries(ExperimentalTimeseries timeseries) {
    this.timeseries = timeseries;
  }

  public LocalDateTime getTime() {
    return time;
  }

  public void setTime(LocalDateTime time) {
    this.time = time;
  }
}
