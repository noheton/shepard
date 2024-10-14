package de.dlr.shepard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public class ExperimentalTimeseriesPayload {

  @JsonIgnore
  private long timeseriesId;

  private LocalDateTime time;

  private double doubleValue;

  private int intValue;

  private String stringValue;

  private boolean booleanValue;

  public double getDoubleValue() {
    return doubleValue;
  }

  public void setDoubleValue(Double floatValue) {
    this.doubleValue = floatValue;
  }

  public int getIntValue() {
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

  public boolean getBooleanValue() {
    return booleanValue;
  }

  public void setBooleanValue(Boolean booleanValue) {
    this.booleanValue = booleanValue;
  }

  public long getTimeseriesId() {
    return timeseriesId;
  }

  public void setTimeseriesId(Long timeseriesId) {
    this.timeseriesId = timeseriesId;
  }

  public LocalDateTime getTime() {
    return time;
  }

  public void setTime(LocalDateTime time) {
    this.time = time;
  }
}
