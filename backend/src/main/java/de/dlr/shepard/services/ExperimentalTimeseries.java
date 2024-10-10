package de.dlr.shepard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseries {

  ExperimentalTimeseries() {}

  @Id
  @JsonIgnore
  private UUID id;

  @Column(name = "container_id")
  @JsonIgnore
  private Long containerId;

  @NotBlank
  private String measurement;

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

  @NotBlank
  @Schema(nullable = true)
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

  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
