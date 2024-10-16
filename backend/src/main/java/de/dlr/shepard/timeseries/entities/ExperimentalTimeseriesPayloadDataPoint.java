package de.dlr.shepard.timeseries.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ExperimentalTimeseriesPayloadDataPoint {

  @JsonIgnore
  private int timeseriesId;

  private long timestamp;

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

  public int getTimeseriesId() {
    return timeseriesId;
  }

  public void setTimeseriesId(int timeseriesId) {
    this.timeseriesId = timeseriesId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
}
