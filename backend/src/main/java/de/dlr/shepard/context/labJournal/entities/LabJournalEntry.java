package de.dlr.shepard.context.labJournal.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.DataObject;
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
    if (!(obj instanceof LabJournalEntry)) return false;
    LabJournalEntry other = (LabJournalEntry) obj;
    return super.equals(other) && content.equals(other.content) && HasId.equalsHelper(dataObject, other.dataObject);
  }
}
