package de.dlr.shepard.context.references.file.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@link FileGroup}, a sub-Reference grouping under a
 * {@code FileBundleReference} (FR1a, see {@code aidocs/53 §1.4}).
 *
 * <p>Used by the {@code /v2/bundles/{appId}/groups/...} endpoints.
 * The fields surface FileGroup metadata plus its list of files
 * (server-side projection — clients also have
 * {@code GET /v2/bundles/{a}/groups/{g}} for the group-by-itself
 * fetch).
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "FileGroup")
public class FileGroupIO extends BasicEntityIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

  @Schema(nullable = true)
  private String description;

  @Schema(description = "0-based ordering index. Clients render groups in ascending index order.")
  private Integer index;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(nullable = true, format = "date-time")
  private Date startedAt;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(nullable = true, format = "date-time")
  private Date endedAt;

  @Schema(nullable = true, description = "Free-form key/value metadata.")
  private Map<String, String> attributes = new HashMap<>();

  @Schema(readOnly = true, description = "Files attached to this group.")
  private List<ShepardFile> files = new ArrayList<>();

  public FileGroupIO(FileGroup src) {
    super(src);
    this.appId = src.getAppId();
    this.description = src.getDescription();
    this.index = src.getIndex();
    this.startedAt = src.getStartedAt();
    this.endedAt = src.getEndedAt();
    this.attributes = src.getAttributes() != null ? new HashMap<>(src.getAttributes()) : new HashMap<>();
    this.files = src.getFiles() != null ? new ArrayList<>(src.getFiles()) : new ArrayList<>();
  }
}
