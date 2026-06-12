package de.dlr.shepard.v2.dataobject.io;

import de.dlr.shepard.context.collection.entities.DataObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "DataObjectSummary", description = "Compact DataObject reference — appId, name, status.")
public class DataObjectSummaryIO {

  @Schema(readOnly = true)
  private String appId;

  @Schema(readOnly = true)
  private String name;

  @Schema(readOnly = true)
  private String status;

  public DataObjectSummaryIO(DataObject d) {
    this.appId = d.getAppId();
    this.name = d.getName();
    this.status = d.getStatus();
  }
}
