package de.dlr.shepard.context.references.file.entities;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Properties;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

/**
 * Sub-Reference grouping under a {@link FileBundleReference}, per
 * FR1a ({@code aidocs/53 §1.3 / §1.4}).
 *
 * <p>A {@code FileGroup} carries the
 * "these N files belong to one logical sub-run" metadata that the flat
 * pre-FR1a {@code FileReference} bag couldn't express:
 * {@link #name}, {@link #description}, {@link #attributes},
 * optional {@link #startedAt} / {@link #endedAt} timestamps, and a
 * stable {@link #index} for client-side ordering.
 *
 * <p><strong>Permissions inherit from the parent bundle</strong>
 * (which inherits from the parent DataObject). FileGroup is navigation
 * + metadata only — no separate ACL surface (per the §1.3 (c) note in
 * the design doc, the Group is not a security boundary).
 *
 * <p>Bytes still live in the bundle's single
 * {@link de.dlr.shepard.data.file.entities.FileContainer} (Mongo
 * collection); the group->file edges are a logical Neo4j-side
 * sub-structure, not a separate storage namespace.
 */
@NodeEntity(label = "FileGroup")
@Data
@NoArgsConstructor
public class FileGroup extends BasicEntity {

  /**
   * Free-form description, mirroring
   * {@link de.dlr.shepard.common.neo4j.entities.AbstractDataObject#getDescription()}.
   */
  private String description;

  /**
   * 0-based ordering index. Clients render groups by ascending index.
   * The default group created by the V21 migration always has
   * {@code index = 0}; new groups created via the {@code /v2/bundles/}
   * surface get {@code max(existing) + 1}.
   */
  private Integer index;

  /** Optional wall-clock start of the sub-run this group represents. */
  @DateLong
  private Date startedAt;

  /** Optional wall-clock end of the sub-run this group represents. */
  @DateLong
  private Date endedAt;

  /**
   * Free-form key/value metadata. Same {@code @Properties(delimiter =
   * "||")} idiom as
   * {@link de.dlr.shepard.common.neo4j.entities.AbstractDataObject#attributes}
   * (post-V9 — see V9 migration) so that storage / search / indexing
   * apply uniformly across primitives.
   */
  @ToString.Exclude
  @Properties(delimiter = "||")
  private Map<String, String> attributes = new HashMap<>();

  /**
   * Files attached directly to this group. The bundle keeps its own
   * {@code HAS_PAYLOAD} edge to the same files as a compatibility
   * shadow for the upstream API (see V21 migration top comment).
   */
  @ToString.Exclude
  @Relationship(type = Constants.HAS_PAYLOAD)
  private List<ShepardFile> files = new ArrayList<>();

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public FileGroup(long id) {
    super(id);
  }

  public void addFile(ShepardFile file) {
    files.add(file);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(description, index, startedAt, endedAt, attributes);
    result = prime * result + HasId.hashcodeHelper(files);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof FileGroup)) return false;
    FileGroup other = (FileGroup) obj;
    return (
      Objects.equals(description, other.description) &&
      Objects.equals(index, other.index) &&
      Objects.equals(startedAt, other.startedAt) &&
      Objects.equals(endedAt, other.endedAt) &&
      Objects.equals(attributes, other.attributes) &&
      HasId.areEqualSetsByUniqueId(files, other.files)
    );
  }
}
