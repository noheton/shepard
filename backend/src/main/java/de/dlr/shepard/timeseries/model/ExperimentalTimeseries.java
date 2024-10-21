package de.dlr.shepard.timeseries.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class ExperimentalTimeseries {

  private final long timeseriesContainerId;

  @NotBlank
  private final String measurement;

  @NotBlank
  private final String field;

  @NotBlank
  @Schema(nullable = true) // Todo: NotBlank und nullable=true wiederspricht sich
  private final String device;

  @NotBlank
  @Schema(nullable = true)
  private final String location;

  @NotBlank
  private final String symbolicName;
}
