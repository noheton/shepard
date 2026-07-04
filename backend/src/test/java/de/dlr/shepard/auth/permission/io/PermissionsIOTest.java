package de.dlr.shepard.auth.permission.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
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
  public void testReaderGroupAppIds() {
    var perm = new Permissions(5L);
    Collection col = new Collection(123L);
    col.setShepardId(134L);
    perm.setEntities(new ArrayList<>(List.of(col)));

    UserGroup g1 = new UserGroup(10L);
    g1.setAppId("018f-appid-reader-group");
    UserGroup g2 = new UserGroup(11L);
    // g2 has no appId (legacy node — null is expected in the output array)
    UserGroup g3 = new UserGroup(12L);
    g3.setAppId("018f-appid-writer-group");
    perm.setReaderGroups(List.of(g1, g2));
    perm.setWriterGroups(List.of(g3));

    var converted = new PermissionsIO(perm);
    assertEquals(2, converted.getReaderGroupIds().length);
    assertEquals(10L, converted.getReaderGroupIds()[0]);
    assertEquals(11L, converted.getReaderGroupIds()[1]);
    assertEquals(2, converted.getReaderGroupAppIds().length);
    assertEquals("018f-appid-reader-group", converted.getReaderGroupAppIds()[0]);
    assertNull(converted.getReaderGroupAppIds()[1]);
    assertEquals(1, converted.getWriterGroupIds().length);
    assertEquals(12L, converted.getWriterGroupIds()[0]);
    assertEquals(1, converted.getWriterGroupAppIds().length);
    assertEquals("018f-appid-writer-group", converted.getWriterGroupAppIds()[0]);
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
