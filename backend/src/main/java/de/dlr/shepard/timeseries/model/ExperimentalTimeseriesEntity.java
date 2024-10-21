package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@NoArgsConstructor
@Entity
@Table(name = "timeseries")
public class ExperimentalTimeseriesEntity {

  @Id
  private int id;

  @Column(name = "container_id")
  private long containerId;

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

  public ExperimentalTimeseriesEntity(
    long containerId,
    @NotBlank String measurement,
    @NotBlank String field,
    @NotBlank String device,
    @NotBlank String location,
    @NotBlank String symbolicName
  ) {
    this.containerId = containerId;
    this.measurement = measurement;
    this.field = field;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
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

  @JsonIgnore
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
