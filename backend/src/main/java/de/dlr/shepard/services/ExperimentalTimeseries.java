package de.dlr.shepard.services;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseries {

  ExperimentalTimeseries() {}

  @Id
  private UUID id;

  private String measurement;
  private String device;
  private String location;

  @Column(name = "symbolic_name")
  private String symbolicName;

  private String field;

  @OneToMany(mappedBy = "timeseries", cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonManagedReference // Prevents recursion in JSON serialization
  private List<ExperimentalTimeseriesPayload> payload;

  public UUID getId() {
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
}
