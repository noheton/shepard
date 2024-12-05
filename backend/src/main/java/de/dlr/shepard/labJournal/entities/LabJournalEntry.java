package de.dlr.shepard.labJournal.entities;

import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LabJournalEntry extends AbstractEntity {

  private String content;

  @Relationship(type = Constants.HAS_LABJOURNAL_ENTRY, direction = Direction.INCOMING)
  private DataObject dataObject;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DataObject)) return false;
    LabJournalEntry other = (LabJournalEntry) obj;
    return super.equals(other) && content.equals(other.content) && HasId.equalsHelper(dataObject, other.dataObject);
  }
}
