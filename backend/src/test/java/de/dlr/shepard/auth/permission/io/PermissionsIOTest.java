package de.dlr.shepard.auth.permission.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class PermissionsIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(PermissionsIO.class).verify();
  }

  @Test
  public void testConversion() {
    var perm = new Permissions(5L);
    Collection col = new Collection(123L);
    col.setShepardId(134L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    colList.add(col);
    perm.setEntities(colList);
    perm.setOwner(new User("bob"));
    perm.setReader(List.of(new User("reader")));
    perm.setWriter(List.of(new User("writer")));
    perm.setManager(List.of(new User("manager")));

    var converted = new PermissionsIO(perm);
    assertEquals(col.getShepardId(), converted.getEntityId());
    assertEquals("bob", converted.getOwner());
    assertEquals("[reader]", Arrays.toString(converted.getReader()));
    assertEquals("[writer]", Arrays.toString(converted.getWriter()));
    assertEquals("[manager]", Arrays.toString(converted.getManager()));
  }

  @Test
  public void testConversion_ownerIsNull() {
    var perm = new Permissions(5L);
    Collection col = new Collection(123L);
    col.setShepardId(134L);
    ArrayList<BasicEntity> colList = new ArrayList<BasicEntity>();
    colList.add(col);
    perm.setEntities(colList);
    perm.setOwner(null);

    var converted = new PermissionsIO(perm);
    assertEquals(col.getShepardId(), converted.getEntityId());
    assertNull(converted.getOwner());
  }

  @Test
  public void testNoVersionableEntity() {
    var perm = new Permissions(5L);
    FileContainer con = new FileContainer(123L);
    ArrayList<BasicEntity> conList = new ArrayList<BasicEntity>();
    conList.add(con);
    perm.setEntities(conList);
    perm.setOwner(null);

    var converted = new PermissionsIO(perm);
    assertEquals(con.getId(), converted.getEntityId());
    assertNull(converted.getOwner());
  }
}
