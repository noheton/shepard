package de.dlr.shepard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseries {

  ExperimentalTimeseries() {}

  @Id
  @JsonIgnore
  private int id;

  @Column(name = "container_id")
  @JsonIgnore
  private int containerId;

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

  public int getId() {
    return id;
  }

  public String getMeasurement() {
    return measurement;
  }

  public void setMeasurement(String measurement) {
    this.measurement = measurement;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  public void setSymbolicName(String symbolicName) {
    this.symbolicName = symbolicName;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
