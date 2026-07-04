package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "DataObjectSummary", description = "Compact DataObject reference — id, appId, name, status, createdAt, createdBy.")
public class DataObjectSummaryIO {

  /** Neo4j OGM node id — kept for backward-compat delete flows until appId-keyed delete ships. */
  @Schema(readOnly = true)
  private Long id;

  @Schema(readOnly = true)
  private String appId;

  @Schema(readOnly = true)
  private String name;

  @Schema(readOnly = true)
  private String status;

  /**
   * PRED-V2-SHAPE: creation timestamp for sorting / display in the Predecessor /
   * Successor panel. ISO-8601 string on the wire. Nullable for entities created
   * before OGM stamped createdAt reliably.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    readOnly = true,
    nullable = true,
    format = "date-time",
    example = "2024-08-15T11:18:44.632+00:00",
    description = "PRED-V2-SHAPE: creation timestamp. Null for pre-OGM rows."
  )
  private Date createdAt;

  /**
   * PRED-V2-SHAPE: display name of the user who created this DataObject.
   * Nullable for entities whose creator account has been purged.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    readOnly = true,
    nullable = true,
    description = "PRED-V2-SHAPE: display name of the creator."
  )
  private String createdBy;

  public DataObjectSummaryIO(DataObject d) {
    this.id = d.getId();
    this.appId = d.getAppId();
    this.name = d.getName();
    this.status = d.getStatus();
    this.createdAt = d.getCreatedAt();
    this.createdBy = d.getCreatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(d.getCreatedBy())
      : null;
  }
}
