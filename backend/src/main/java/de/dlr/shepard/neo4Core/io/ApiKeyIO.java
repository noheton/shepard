package de.dlr.shepard.neo4Core.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "ApiKey")
public class ApiKeyIO {

  @Schema(readOnly = true)
  private UUID uid;

  @NotBlank
  @Schema(nullable = true)
  private String name;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(readOnly = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  private Date createdAt;

  @Schema(readOnly = true)
  private String belongsTo;

  public ApiKeyIO(ApiKey key) {
    this.uid = key.getUid();
    this.name = key.getName();
    this.createdAt = key.getCreatedAt();
    this.belongsTo = key.getBelongsTo() != null ? key.getBelongsTo().getUsername() : null;
  }
}
