package de.dlr.shepard.context.collection.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class DataObject extends AbstractDataObject {

  @Relationship(type = Constants.HAS_DATAOBJECT, direction = Direction.INCOMING)
  private Collection collection;

  @Relationship(type = Constants.HAS_REFERENCE)
  private List<BasicReference> references = new ArrayList<>();

  @Relationship(type = Constants.HAS_SUCCESSOR, direction = Direction.OUTGOING)
  private List<DataObject> successors = new ArrayList<>();

  @Relationship(type = Constants.HAS_SUCCESSOR, direction = Direction.INCOMING)
  private List<DataObject> predecessors = new ArrayList<>();

  @Relationship(type = Constants.HAS_CHILD, direction = Direction.OUTGOING)
  private List<DataObject> children = new ArrayList<>();

  @Relationship(type = Constants.HAS_CHILD, direction = Direction.INCOMING)
  private DataObject parent;

  @Relationship(type = Constants.POINTS_TO, direction = Direction.INCOMING)
  private List<DataObjectReference> incoming = new ArrayList<>();

  @Relationship(type = Constants.HAS_LABJOURNAL_ENTRY)
  private List<LabJournalEntry> labJournalEntries = new ArrayList<>();

  /**
   * PROV1j — EU AI Act Art. 50 per-artefact visibility field.
   *
   * <p>Indicates the agent mode under which this DataObject was created:
   * <ul>
   *   <li>{@code null} — default, equivalent to {@code "human"} (human-authored)</li>
   *   <li>{@code "human"} — explicitly human-authored</li>
   *   <li>{@code "ai"} — created by an AI agent (X-AI-Agent header present on the
   *       creating request, or caller set it explicitly)</li>
   *   <li>{@code "collaborative"} — human + AI collaborative creation</li>
   * </ul>
   *
   * <p>Stored as a plain Neo4j node property. OGM maps the field by name — no
   * {@code @Property} annotation required. Value is immutable after creation;
   * the v2 PATCH path does not expose this field (it is not on {@code DataObjectIO}).
   */
  private String provenanceMode;

  /**
   * PROV1k — typed predecessor relationships serialised as JSON.
   *
   * <p>Stores a JSON array of
   * {@link de.dlr.shepard.v2.dataobject.io.TypedPredecessorIO} records
   * so each predecessor edge can carry a PROV-O / FAIR²R relationship type
   * ({@code "prov:wasInformedBy"}, {@code "prov:wasRevisionOf"},
   * {@code "fair2r:repairs"}) without requiring a Neo4j
   * {@code @RelationshipEntity}.
   *
   * <p>Schema-free additive property — older DataObjects that predate PROV1k
   * have {@code typedPredecessorsJson = null}; the service falls back to the
   * untyped {@code predecessors} relationship list in that case.
   *
   * <p>Populated by
   * {@link de.dlr.shepard.context.collection.services.DataObjectService}
   * when a {@link de.dlr.shepard.v2.dataobject.io.CreateDataObjectV2IO}
   * request body carries a non-empty {@code typedPredecessors} list.
   */
  private String typedPredecessorsJson;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public DataObject(long id) {
    super(id);
  }

  public void addReference(BasicReference reference) {
    references.add(reference);
  }

  public void addSuccessor(DataObject successor) {
    successors.add(successor);
  }

  public void addPredecessor(DataObject predecessor) {
    predecessors.add(predecessor);
  }

  public void addChild(DataObject child) {
    children.add(child);
    child.setParent(this);
  }

  public void addIncoming(DataObjectReference dataObjectReference) {
    incoming.add(dataObjectReference);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(collection);
    result = prime * result + HasId.hashcodeHelper(references);
    result = prime * result + HasId.hashcodeHelper(successors);
    result = prime * result + HasId.hashcodeHelper(predecessors);
    result = prime * result + HasId.hashcodeHelper(children);
    result = prime * result + HasId.hashcodeHelper(parent);
    result = prime * result + HasId.hashcodeHelper(incoming);
    result = prime * result + HasId.hashcodeHelper(labJournalEntries);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof DataObject)) return false;
    DataObject other = (DataObject) obj;
    return (
      HasId.equalsHelper(collection, other.collection) &&
      HasId.areEqualSetsByUniqueId(references, other.references) &&
      HasId.areEqualSetsByUniqueId(predecessors, other.predecessors) &&
      HasId.areEqualSetsByUniqueId(successors, other.successors) &&
      HasId.equalsHelper(parent, other.parent) &&
      HasId.areEqualSetsByUniqueId(children, other.children) &&
      HasId.areEqualSetsByUniqueId(incoming, other.incoming) &&
      HasId.areEqualSetsByUniqueId(labJournalEntries, other.labJournalEntries)
    );
  }
}
