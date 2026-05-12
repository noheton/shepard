package de.dlr.shepard.v2.bundle.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import de.dlr.shepard.context.references.file.io.FileGroupIO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code /v2/} wire shape for {@link FileBundleReference} (FR1a, see
 * {@code aidocs/53 §1.6}).
 *
 * <p>Differences vs. the upstream {@link
 * de.dlr.shepard.context.references.file.io.FileReferenceIO}:
 *
 * <ul>
 *   <li>Carries {@code groups: List<FileGroupIO>} populated from the
 *       bundle's {@code HAS_GROUP} edges (the upstream IO carries only
 *       the flat {@code fileOids} array — kept for byte-compatibility
 *       with {@code /shepard/api/}).</li>
 *   <li>Surfaces {@code appId} (UUID v7) — the {@code /v2/} routing
 *       convention per {@code aidocs/25 L2d}.</li>
 *   <li>Surfaces {@code containerMongoId} so clients can address bytes
 *       via existing GridFS routes.</li>
 * </ul>
 *
 * <p>Consumed by {@link
 * de.dlr.shepard.v2.bundle.resources.FileBundleReferenceRest}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "FileBundleReference")
public class FileBundleReferenceIO extends BasicReferenceIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

  @Schema(readOnly = true, nullable = true, description = "Mongo ObjectId of the underlying FileContainer.")
  private String containerMongoId;

  @Schema(
    readOnly = true,
    description = "Sub-Reference grouping — at least one (the default group, named 'default') for every bundle."
  )
  private List<FileGroupIO> groups = new ArrayList<>();

  public FileBundleReferenceIO(FileBundleReference src) {
    super(src);
    this.appId = src.getAppId();
    this.containerMongoId = src.getFileContainer() != null ? src.getFileContainer().getMongoId() : null;
    if (src.getGroups() != null) {
      this.groups = src.getGroups().stream()
        .sorted(Comparator.comparingInt((FileGroup g) -> g.getIndex() == null ? 0 : g.getIndex()))
        .map(FileGroupIO::new)
        .toList();
    }
  }
}
