package de.dlr.shepard.common.neo4j.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "BasicEntity")
public class BasicEntityIO implements HasId {

  @Schema(readOnly = true, required = true)
  private Long id;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, required = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @Schema(readOnly = true, required = true)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    readOnly = true,
    nullable = true,
    required = true,
    format = "date-time",
    example = "2024-08-15T11:18:44.632+00:00"
  )
  private Date updatedAt;

  @Schema(readOnly = true, nullable = true, required = true)
  private String updatedBy;

  @NotBlank
  @Schema(required = true)
  private String name;

  @Schema(readOnly = true, nullable = true, description = "Application-level UUID v7 identifier.")
  private String appId;

  /**
   * Monotonically increasing write counter. Starts at 1 on creation and
   * increments on every subsequent write. Populated for {@link VersionableEntity}
   * instances (Collection, DataObject, *Reference). Zero for non-versionable
   * entities (where revision tracking does not apply). Server-managed — read-only
   * for clients.
   */
  @Schema(readOnly = true, description = "Write counter (V2a revision). 0 for non-versionable entities.")
  private long revision;

  public BasicEntityIO(BasicEntity entity) {
    this.id = entity.getId();
    this.appId = entity.getAppId();
    this.createdAt = entity.getCreatedAt();
    this.createdBy = entity.getCreatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(entity.getCreatedBy())
      : null;
    this.updatedAt = entity.getUpdatedAt();
    this.updatedBy = entity.getUpdatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(entity.getUpdatedBy())
      : null;
    this.name = entity.getName();
  }

  public BasicEntityIO(BasicEntityIO entity) {
    this.id = entity.getId();
    this.appId = entity.getAppId();
    this.createdAt = entity.getCreatedAt();
    this.createdBy = entity.getCreatedBy() != null ? entity.getCreatedBy() : null;
    this.updatedAt = entity.getUpdatedAt();
    this.updatedBy = entity.getUpdatedBy() != null ? entity.getUpdatedBy() : null;
    this.name = entity.getName();
    this.revision = entity.getRevision();
  }

  public BasicEntityIO(VersionableEntity entity) {
    this.id = entity.getShepardId();
    this.appId = entity.getAppId();
    this.createdAt = entity.getCreatedAt();
    this.createdBy = entity.getCreatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(entity.getCreatedBy())
      : null;
    this.updatedAt = entity.getUpdatedAt();
    this.updatedBy = entity.getUpdatedBy() != null
      ? DisplayNameResolver.effectiveDisplayName(entity.getUpdatedBy())
      : null;
    this.name = entity.getName();
    this.revision = entity.getRevision();
  }

  protected static long[] extractIds(List<? extends BasicEntity> entities) {
    return entities.stream().map(BasicEntity::getId).mapToLong(Long::longValue).toArray();
  }

  protected static long[] extractShepardIds(List<? extends VersionableEntity> entities) {
    return entities.stream().mapToLong(e -> {
      Long sid = e.getShepardId();
      // Compiled plugins (e.g. git plugin) may persist VersionableEntity rows with a single
      // createOrUpdate call, leaving shepardId null. Fall back to the Neo4j internal id so
      // the containing IO constructor doesn't NPE on Long::longValue.
      if (sid != null) return sid;
      Long neoId = e.getId();
      return neoId != null ? neoId : 0L;
    }).toArray();
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (this.getClass() != o.getClass()) return false;
    BasicEntityIO other = (BasicEntityIO) o;
    return (
      Objects.equals(id, other.id) &&
      Objects.equals(appId, other.appId) &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(createdBy, other.createdBy) &&
      Objects.equals(updatedAt, other.updatedAt) &&
      Objects.equals(updatedBy, other.updatedBy) &&
      Objects.equals(name, other.name)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 0;
    result = prime * result + Objects.hash(id, appId, createdAt, createdBy, updatedAt, updatedBy, name);
    return result;
  }
}
