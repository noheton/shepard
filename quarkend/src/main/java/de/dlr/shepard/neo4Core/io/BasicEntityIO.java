package de.dlr.shepard.neo4Core.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.neo4Core.entities.BasicEntity;
import de.dlr.shepard.util.HasId;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "BasicEntity")
public class BasicEntityIO implements HasId {

  @Schema(readOnly = true)
  private Long id;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true)
  private Date createdAt;

  @Schema(readOnly = true)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, nullable = true)
  private Date updatedAt;

  @Schema(readOnly = true, nullable = true)
  private String updatedBy;

  @NotBlank
  @Schema(nullable = true)
  private String name;

  public BasicEntityIO(BasicEntity entity) {
    this.id = entity.getId();
    this.createdAt = entity.getCreatedAt();
    this.createdBy = entity.getCreatedBy() != null ? entity.getCreatedBy().getUsername() : null;
    this.updatedAt = entity.getUpdatedAt();
    this.updatedBy = entity.getUpdatedBy() != null ? entity.getUpdatedBy().getUsername() : null;
    this.name = entity.getName();
  }

  protected static long[] extractIds(List<? extends BasicEntity> entities) {
    var result = entities.stream().map(BasicEntity::getId).mapToLong(Long::longValue).toArray();
    return result;
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
