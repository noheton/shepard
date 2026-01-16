package de.dlr.shepard.context.semantic.io;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "AnnotatableTimeseries")
public class AnnotatableTimeseriesIO implements HasId {

  @Schema(readOnly = true, required = true)
  private Long id;

  @Schema(readOnly = true, required = true)
  private Long containerId;

  @Schema(readOnly = true, required = true)
  private Integer timeseriesId;

  @Schema(readOnly = true, required = true)
  private long[] annotationIds;

  public AnnotatableTimeseriesIO(AnnotatableTimeseries annoTS) {
    this.id = annoTS.getId();
    this.containerId = annoTS.getContainerId();
    this.timeseriesId = annoTS.getTimeseriesId();
    annotationIds = new long[annoTS.getAnnotations().size()];
    for (int i = 0; i < annoTS.getAnnotations().size(); i++) annotationIds[i] = annoTS.getAnnotations().get(i).getId();
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
