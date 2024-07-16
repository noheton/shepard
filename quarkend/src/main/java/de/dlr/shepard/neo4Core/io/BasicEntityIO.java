package de.dlr.shepard.neo4Core.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.neo4Core.entities.BasicEntity;
import de.dlr.shepard.util.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "BasicEntity")
public class BasicEntityIO implements HasId {

  @Schema(accessMode = AccessMode.READ_ONLY)
  private Long id;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(accessMode = AccessMode.READ_ONLY)
  private Date createdAt;

  @Schema(accessMode = AccessMode.READ_ONLY)
  private String createdBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
  private Date updatedAt;

  @Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
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
