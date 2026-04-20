package de.dlr.shepard.context.collection.entities;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.neo4j.entities.HasPermissions;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.data.file.entities.FileContainer;
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
public class Collection extends AbstractDataObject implements HasPermissions {

  @Relationship(type = Neo4jLabels.HAS_DATAOBJECT)
  private List<DataObject> dataObjects = new ArrayList<>();

  @Relationship(type = Neo4jLabels.POINTS_TO, direction = Direction.INCOMING)
  private List<CollectionReference> incoming = new ArrayList<>();

  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  @Relationship(type = Neo4jLabels.HAS_DEFAULT_FILE_CONTAINER)
  private FileContainer fileContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public Collection(long id) {
    super(id);
  }

  /**
   * Add one related DataObject
   *
   * @param dataObject the dataObject to add
   */
  public void addDataObject(DataObject dataObject) {
    dataObjects.add(dataObject);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObjects);
    result = prime * result + HasId.hashcodeHelper(incoming);
    result = prime * result + HasId.hashcodeHelper(permissions);
    result = prime * result + HasId.hashcodeHelper(version);
    result = prime * result + HasId.hashcodeHelper(fileContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof Collection)) return false;
    Collection other = (Collection) obj;
    return (
      HasId.areEqualSetsByUniqueId(dataObjects, other.dataObjects) &&
      HasId.areEqualSetsByUniqueId(incoming, other.incoming) &&
      HasId.equalsHelper(permissions, other.permissions) &&
      HasId.equalsHelper(version, other.version) &&
      HasId.equalsHelper(fileContainer, other.fileContainer)
    );
  }
}
