package de.dlr.shepard.context.references.file.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

/**
 * Singleton {@code FileReference} entity, FR1b per
 * {@code aidocs/53 §1.8}. Holds exactly one {@link ShepardFile}.
 *
 * <p><strong>Why a separate label from {@link FileBundleReference}?</strong>
 * FR1a renamed the multi-file primitive to {@link FileBundleReference}
 * but kept the legacy Neo4j {@code :FileReference} label on every
 * bundle node so the upstream-frozen
 * {@code /shepard/api/.../fileReferences/...} REST surface keeps
 * working byte-for-byte (CLAUDE.md API-version policy). That means
 * {@code :FileReference} is already taken — every node with that
 * label is a bundle.
 *
 * <p>FR1b's singleton therefore uses a <strong>different</strong>
 * Neo4j label, {@code :SingletonFileReference}, to make the two
 * shapes unambiguous to the OGM and to direct Cypher. The
 * upstream-API DAOs ({@link
 * de.dlr.shepard.context.references.file.daos.FileBundleReferenceDAO})
 * continue to match {@code :FileReference} and so continue to see
 * only bundles, which is exactly the §1.8.4 invariant: <em>writes on
 * the upstream API only ever create bundles, so the upstream surface
 * produces no type-ambiguity</em>.
 *
 * <p>Singletons participate in the cross-Reference
 * {@code (d)-[:has_reference]->(r)} traversal via the shared
 * {@code BasicReference} label-set inherited from
 * {@link BasicReference}; the {@code :Reference} label that the OGM
 * writes on every {@code BasicReference}-derived node is sufficient
 * for the DataObject-side join.
 *
 * <p><strong>Bytes.</strong> Per §1.8.3 the singleton's
 * {@link ShepardFile} lives in a shared {@code shepard-files} GridFS
 * namespace (one Mongo collection used by every singleton), not in a
 * per-Reference {@link de.dlr.shepard.data.file.entities.FileContainer}.
 * This is the structural difference from {@link FileBundleReference}'s
 * one-Mongo-collection-per-bundle layout: a researcher uploading
 * 10 000 PDFs would otherwise pay 10 000 empty {@code FileContainer}
 * docs.
 *
 * <p><strong>Upstream-frozen surface.</strong> The class name
 * {@code FileReference} is reclaimed for the singleton; the legacy
 * Java identifier {@code FileReferenceIO} (upstream IO wire shape)
 * and {@code FileReferenceRest} (upstream REST class) stay on the
 * existing bundle path — those identifiers are bound to the
 * {@code /shepard/api/.../fileReferences/...} wire and are frozen
 * forever per CLAUDE.md §API-version policy. The singleton's
 * {@code /v2/files/...} wire shape lives in
 * {@code de.dlr.shepard.v2.file.io.FileReferenceV2IO} +
 * {@code de.dlr.shepard.v2.file.resources.FileReferenceV2Rest}.
 *
 * <p>This entity therefore has <em>no</em> upstream wire-shape
 * obligation — it surfaces exclusively under {@code /v2/}.
 */
@NodeEntity(label = "SingletonFileReference")
@Data
@NoArgsConstructor
public class FileReference extends BasicReference {

  /**
   * The single attached {@link ShepardFile}. Persisted via the same
   * {@code HAS_PAYLOAD} edge type that {@link FileBundleReference}
   * uses, so cross-Reference graph queries that ask "what files does
   * this Reference point at?" read both shapes uniformly. The
   * cardinality difference (singleton vs. multi) is enforced at the
   * service layer — Neo4j doesn't enforce edge cardinality, and we
   * don't want to lose graph-level read uniformity by switching to a
   * different relationship type just for the count constraint.
   */
  @ToString.Exclude
  @Relationship(type = Constants.HAS_PAYLOAD)
  private ShepardFile file;

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public FileReference(long id) {
    super(id);
  }

  /**
   * Singletons surface their own class-simple-name {@code "FileReference"}
   * as the entity type. The legacy {@link FileBundleReference}
   * overrides {@code getType()} to return the same string
   * {@code "FileReference"} for upstream-API wire compatibility — both
   * types share the type string because {@code /shepard/api/} reads
   * project both onto the legacy bundle wire shape (§1.8.4).
   *
   * <p>Internal {@code switch(reference.getType())} call sites in
   * {@code ExportService} / {@code EntityUrlSynthesiser} therefore
   * route on the same key. Discrimination between singleton and
   * bundle happens at the {@code instanceof} level when those code
   * paths need it.
   */
  @Override
  public String getType() {
    return "FileReference";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(file);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof FileReference)) return false;
    FileReference other = (FileReference) obj;
    return HasId.equalsHelper(file, other.file) && Objects.equals(file, other.file);
  }
}
