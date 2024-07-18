package de.dlr.shepard.influxDB;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.util.HasId;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@NoArgsConstructor
public class Timeseries implements HasId {

  @Id
  @GeneratedValue
  @JsonIgnore
  private Long id;

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
  private String symbolicName;

  @NotBlank
  @Schema(nullable = true)
  private String field;

  public Timeseries(String measurement, String device, String location, String symbolicName, String field) {
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
  }

  @Override
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
