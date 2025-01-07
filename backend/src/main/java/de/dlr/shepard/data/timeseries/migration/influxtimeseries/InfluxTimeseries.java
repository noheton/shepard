package de.dlr.shepard.data.timeseries.migration.influxtimeseries;

import de.dlr.shepard.common.util.HasId;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InfluxTimeseries implements HasId {

  @NotBlank
  private String measurement;

  @NotBlank
  private String device;

  @NotBlank
  private String location;

  @NotBlank
  private String symbolicName;

  @NotBlank
  private String field;

  public InfluxTimeseries(String measurement, String device, String location, String symbolicName, String field) {
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
