package de.dlr.shepard.context.version.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.context.version.entities.Version;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "Version")
public class VersionIO {

  @Schema(readOnly = true, required = true)
  private UUID uid;

  private String name;

  private String description;

  private boolean isHEADVersion;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @Schema(readOnly = true)
  private String createdBy;

  @Schema(readOnly = true)
  private UUID predecessorUUID;

  public VersionIO(Version version) {
    this.uid = version.getUid();
    this.name = version.getName();
    this.isHEADVersion = version.isHEADVersion();
    this.description = version.getDescription();
    this.createdAt = version.getCreatedAt();
    this.createdBy = DisplayNameResolver.effectiveDisplayName(version.getCreatedBy());
    if (version.getPredecessor() != null) this.predecessorUUID = version.getPredecessor().getUid();
    else this.predecessorUUID = null;
  }
}
