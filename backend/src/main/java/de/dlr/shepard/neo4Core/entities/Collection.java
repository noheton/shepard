package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
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
public class Collection extends AbstractDataObject {

  @Relationship(type = Constants.HAS_DATAOBJECT)
  private List<DataObject> dataObjects = new ArrayList<>();

  @Relationship(type = Constants.POINTS_TO, direction = Direction.INCOMING)
  private List<CollectionReference> incoming = new ArrayList<>();

  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  @Relationship(type = Constants.HAS_VERSION)
  private Version version;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public Collection(long id) {
    super(id);
  }

  // copy constructor
  public Collection(Collection collection) {
    this.setAnnotations(collection.getAnnotations());
    this.setAttributes(collection.getAttributes());
    this.setCreatedAt(collection.getCreatedAt());
    this.setCreatedBy(collection.getCreatedBy());
    this.setDataObjects(collection.getDataObjects());
    this.setDescription(collection.getDescription());
    this.setIncoming(collection.getIncoming());
    this.setName(collection.getName());
    this.setPermissions(collection.getPermissions());
    this.setShepardId(collection.getShepardId());
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
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof Collection)) return false;
    Collection other = (Collection) obj;
    return (
      HasId.equalsHelper(dataObjects, other.dataObjects) &&
      HasId.equalsHelper(incoming, other.incoming) &&
      HasId.equalsHelper(permissions, other.permissions) &&
      HasId.equalsHelper(version, other.version)
    );
  }
}
