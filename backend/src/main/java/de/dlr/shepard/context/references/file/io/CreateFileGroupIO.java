package de.dlr.shepard.context.references.file.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for {@code POST /v2/bundles/{bundleAppId}/groups}.
 * The server fills in {@code appId} (UUID v7) and computes {@code index}
 * as {@code max(existing index) + 1} when the client doesn't supply
 * one.
 */
@Data
@NoArgsConstructor
@Schema(name = "CreateFileGroup")
public class CreateFileGroupIO {

  @Schema(required = true, description = "Display name; need not be unique within the bundle.")
  private String name;

  @Schema(nullable = true)
  private String description;

  @Schema(nullable = true, description = "0-based index. Server-assigned when absent.")
  private Integer index;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(nullable = true, format = "date-time")
  private Date startedAt;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(nullable = true, format = "date-time")
  private Date endedAt;

  @Schema(nullable = true)
  private Map<String, String> attributes = new HashMap<>();
}
