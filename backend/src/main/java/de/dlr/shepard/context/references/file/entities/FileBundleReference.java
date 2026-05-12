package de.dlr.shepard.context.references.file.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Labels;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

/**
 * A bag of {@link ShepardFile}s fronted by exactly one
 * {@link FileContainer}, optionally organised into one or more
 * {@link FileGroup}s (FR1a, see {@code aidocs/53 §1.4}).
 *
 * <p><strong>Naming history.</strong> This class is the renamed
 * descendant of the original {@code FileReference} per FR1a
 * ({@code aidocs/53 §1.7}). Two Neo4j labels are written on every
 * persist:
 * <ul>
 *   <li>The legacy {@code :FileReference} label — declared via
 *       {@code @NodeEntity(label = "FileReference")} below — is
 *       preserved indefinitely so the upstream-frozen
 *       {@code /shepard/api/.../fileReferences/...} REST surface keeps
 *       working byte-for-byte (CLAUDE.md API-version policy: no
 *       wire-shape change to the upstream API ever) and external
 *       Cypher (provenance / export / version-copy DAOs) that
 *       matches {@code (:FileReference)} keeps working without
 *       touching it.</li>
 *   <li>The new {@code :FileBundleReference} label is added via the
 *       {@code @Labels}-annotated {@link #extraLabels} field on every
 *       newly-saved entity, and via the V21 Cypher migration
 *       (idempotent + fail-fast) for every pre-FR1a row. Asserted by
 *       {@code FileBundleReferenceTest}.</li>
 * </ul>
 *
 * <p>Future shape (FR1b, see {@code aidocs/53 §1.8}): a sibling
 * {@code FileReference} singleton entity will be (re-)introduced for
 * the single-file case. FR1a leaves that for FR1b — every existing row
 * here remains a bundle, even when it holds exactly one file.
 */
@NodeEntity(label = "FileReference")
@Data
@NoArgsConstructor
public class FileBundleReference extends BasicReference {

  /**
   * Two-label OGM trick: this {@code @Labels}-annotated field is
   * persisted as additional dynamic labels on the node. We pin it to a
   * single immutable value so every newly-saved
   * {@link FileBundleReference} ends up with both
   * {@code :FileReference} (from {@code @NodeEntity(label = ...)}) and
   * {@code :FileBundleReference}.
   *
   * <p>The list is initialised in the field initialiser so even
   * Lombok-generated no-arg construction produces a row with the
   * second label. {@link Labels} is the OGM-native way to add labels
   * beyond the {@code @NodeEntity} declaration; it's evaluated on each
   * persist, so subsequent re-saves don't drop the label.
   */
  @Labels
  private List<String> extraLabels = new ArrayList<>(List.of("FileBundleReference"));

  @Relationship(type = Constants.HAS_PAYLOAD)
  private List<ShepardFile> files = new ArrayList<>();

  /**
   * Sub-Reference grouping introduced in FR1a. Newly-created bundles
   * get a default group (named {@code "default"}, {@code index = 0})
   * via
   * {@link de.dlr.shepard.context.references.file.services.FileBundleReferenceService};
   * the V21 migration backfills one for every pre-FR1a row.
   *
   * <p>Bytes still live in the bundle's single {@link FileContainer}
   * (Mongo collection); the group is a logical Neo4j-side
   * sub-structure, not a separate storage namespace.
   */
  @ToString.Exclude
  @Relationship(type = Constants.HAS_GROUP)
  private List<FileGroup> groups = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.IS_IN_CONTAINER)
  private FileContainer fileContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public FileBundleReference(long id) {
    super(id);
  }

  /**
   * Pinned to {@code "FileReference"} for upstream-API wire
   * compatibility (CLAUDE.md API-version policy). The legacy
   * {@code /shepard/api/.../fileReferences/...} surface emits this
   * string in the {@code type} field of every BasicReferenceIO; clients
   * keyed off that exact string would break if we let the default
   * {@code getClass().getSimpleName()} flow through after the rename.
   *
   * <p>The internal {@code switch(reference.getType())} call sites in
   * {@code ExportService} / {@code EntityUrlSynthesiser} also rely on
   * this string staying {@code "FileReference"}.
   */
  @Override
  public String getType() {
    return "FileReference";
  }

  public void addFile(ShepardFile file) {
    files.add(file);
  }

  public void addGroup(FileGroup group) {
    groups.add(group);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(files);
    result = prime * result + HasId.hashcodeHelper(fileContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof FileBundleReference)) return false;
    FileBundleReference other = (FileBundleReference) obj;
    return HasId.equalsHelper(fileContainer, other.fileContainer) && Objects.equals(files, other.files);
  }
}
