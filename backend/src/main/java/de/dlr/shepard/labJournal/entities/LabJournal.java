package de.dlr.shepard.labJournal.entities;

import de.dlr.shepard.neo4Core.entities.AbstractDataObject;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Data;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
public class LabJournal extends AbstractDataObject {

  @Relationship(type = Constants.HAS_LABJOURNAL, direction = Direction.INCOMING)
  private DataObject dataObject;

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObject);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    if (!(obj instanceof DataObject)) return false;
    LabJournal other = (LabJournal) obj;
    return (HasId.equalsHelper(dataObject, other.dataObject));
  }
}
