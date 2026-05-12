package de.dlr.shepard.v2.file.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code /v2/files/...} wire shape for the FR1b singleton
 * {@link FileReference} (see {@code aidocs/53 §1.8.4 / §1.8.6}).
 *
 * <p><strong>Why not name this class {@code FileReferenceIO}?</strong>
 * That identifier is already taken by the upstream-frozen wire shape
 * at {@code de.dlr.shepard.context.references.file.io.FileReferenceIO},
 * which carries the bag-of-files JSON shape that
 * {@code /shepard/api/.../fileReferences/...} clients depend on
 * byte-for-byte (CLAUDE.md §API-version policy). FR1b therefore
 * publishes the singleton wire shape under the
 * {@code FileReferenceV2IO} name in the {@code de.dlr.shepard.v2}
 * sub-package — same naming pattern as FR1a's
 * {@code FileBundleReferenceIO} sitting at
 * {@code de.dlr.shepard.v2.bundle.io}.
 *
 * <p>Schema differences vs. the upstream {@code FileReferenceIO}:
 * <ul>
 *   <li>Carries one inlined {@link ShepardFile} via {@link #file}
 *       (the upstream IO carries a {@code fileOids: String[]} bag).</li>
 *   <li>Surfaces {@code appId} (UUID v7) — the canonical {@code /v2/}
 *       identifier per {@code aidocs/25 L2d}.</li>
 *   <li>No {@code fileContainerId} — singleton bytes live in the
 *       shared {@code shepard-files} namespace, not in a
 *       per-Reference FileContainer (§1.8.3).</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Schema(name = "FileReferenceV2", description = "Singleton FileReference (FR1b) — exactly one ShepardFile.")
public class FileReferenceV2IO extends BasicReferenceIO {

  @Schema(readOnly = true, nullable = true, description = "Application identifier (UUID v7).")
  private String appId;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "The single attached file. May be null in degenerate-row cases (missing GridFS blob)."
  )
  private ShepardFile file;

  /**
   * Construct from the persisted entity. The {@link
   * BasicReferenceIO#getType()} field flows through as
   * {@code "FileReference"} — same as
   * {@code FileBundleReference.getType()} — because the upstream
   * legacy API projects both types onto the same {@code type} string
   * (§1.8.4). Discrimination at the {@code /v2/} layer is by path
   * segment ({@code /v2/files/} vs. {@code /v2/bundles/}), not by
   * the {@code type} field.
   */
  public FileReferenceV2IO(FileReference src) {
    super(src);
    this.appId = src.getAppId();
    this.file = src.getFile();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    FileReferenceV2IO other = (FileReferenceV2IO) o;
    return Objects.equals(appId, other.appId) && Objects.equals(file, other.file);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hashCode(appId);
    result = prime * result + Objects.hashCode(file);
    return result;
  }
}
