package de.dlr.shepard.auth.permission.entities;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.PermissionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
@NoArgsConstructor
public class Permissions implements HasId {

  @Id
  @GeneratedValue
  private Long id;

  @Relationship(type = Constants.HAS_PERMISSIONS, direction = Direction.INCOMING)
  private List<BasicEntity> entities;

  @Relationship(type = Constants.OWNED_BY, direction = Direction.OUTGOING)
  private User owner;

  private PermissionType permissionType;

  @ToString.Exclude
  @Relationship(type = Constants.READABLE_BY, direction = Direction.OUTGOING)
  private List<User> reader = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.WRITEABLE_BY, direction = Direction.OUTGOING)
  private List<User> writer = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.READABLE_BY_GROUP, direction = Direction.OUTGOING)
  private List<UserGroup> readerGroups = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.WRITEABLE_BY_GROUP, direction = Direction.OUTGOING)
  private List<UserGroup> writerGroups = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.MANAGEABLE_BY, direction = Direction.OUTGOING)
  private List<User> manager = new ArrayList<>();

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public Permissions(long id) {
    this.id = id;
  }

  public Permissions(BasicEntity entity, User owner, PermissionType permissionType) {
    this.entities = List.of(entity);
    this.owner = owner;
    this.permissionType = permissionType;
  }

  public Permissions(
    User owner,
    List<User> reader,
    List<User> writer,
    List<UserGroup> readerGroups,
    List<UserGroup> writerGroups,
    List<User> manager,
    PermissionType permissionType
  ) {
    this.owner = owner;
    this.reader = reader;
    this.writer = writer;
    this.writerGroups = writerGroups;
    this.readerGroups = readerGroups;
    this.manager = manager;
    this.permissionType = permissionType;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(id, permissionType, readerGroups, writerGroups);
    result = prime * result + HasId.hashcodeHelper(entities);
    result = prime * result + HasId.hashcodeHelper(owner);
    result = prime * result + HasId.hashcodeHelper(reader);
    result = prime * result + HasId.hashcodeHelper(writer);
    result = prime * result + HasId.hashcodeHelper(manager);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Permissions)) return false;
    Permissions other = (Permissions) obj;
    return (
      Objects.equals(id, other.id) &&
      Objects.equals(permissionType, other.permissionType) &&
      Objects.equals(readerGroups, other.readerGroups) &&
      Objects.equals(writerGroups, other.writerGroups) &&
      HasId.areEqualSetsByUniqueId(entities, other.entities) &&
      HasId.equalsHelper(owner, other.owner) &&
      HasId.areEqualSetsByUniqueId(reader, other.reader) &&
      HasId.areEqualSetsByUniqueId(writer, other.writer) &&
      HasId.areEqualSetsByUniqueId(manager, other.manager)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
