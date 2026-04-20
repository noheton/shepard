package de.dlr.shepard.context.collection.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
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

  @Relationship(type = Neo4jLabels.HAS_DATAOBJECT, direction = Direction.INCOMING)
  private Collection collection;

  @Relationship(type = Neo4jLabels.HAS_REFERENCE)
  private List<BasicReference> references = new ArrayList<>();

  @Relationship(type = Neo4jLabels.HAS_SUCCESSOR, direction = Direction.OUTGOING)
  private List<DataObject> successors = new ArrayList<>();

  @Relationship(type = Neo4jLabels.HAS_SUCCESSOR, direction = Direction.INCOMING)
  private List<DataObject> predecessors = new ArrayList<>();

  @Relationship(type = Neo4jLabels.HAS_CHILD, direction = Direction.OUTGOING)
  private List<DataObject> children = new ArrayList<>();

  @Relationship(type = Neo4jLabels.HAS_CHILD, direction = Direction.INCOMING)
  private DataObject parent;

  @Relationship(type = Neo4jLabels.POINTS_TO, direction = Direction.INCOMING)
  private List<DataObjectReference> incoming = new ArrayList<>();

  @Relationship(type = Neo4jLabels.HAS_LABJOURNAL_ENTRY)
  private List<LabJournalEntry> labJournalEntries = new ArrayList<>();

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
