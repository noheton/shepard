package de.dlr.shepard.testing.fixtures;

import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aBasicReference;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aCollection;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aDataObject;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.aUser;
import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.permissionsFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ShepardTestFixtures}.
 *
 * <p>One assertion-cluster per builder, covering "default values are
 * sensible" (no nulls in non-nullable fields, fresh {@code appId} per
 * call, unique numeric ids, {@code createdAt} stamped).
 */
public class ShepardTestFixturesTest {

  // ---------- User ----------

  @Test
  public void userDefaultsAreSensible() {
    User user = aUser().build();

    assertNotNull(user.getUsername(), "username must default to a non-null value");
    assertFalse(user.getUsername().isEmpty(), "username default must be non-empty");
    assertEquals("", user.getFirstName());
    assertEquals("", user.getLastName());
    assertEquals("", user.getEmail());
    assertNotNull(user.getAppId(), "appId must default to a fresh UUID");
    assertNull(user.getOrcid(), "orcid stays null until explicitly set");
    assertNull(user.getDisplayName(), "displayName stays null until explicitly set");
  }

  @Test
  public void userBuilderProducesFreshAppIdPerCall() {
    User a = aUser().build();
    User b = aUser().build();
    assertNotEquals(a.getAppId(), b.getAppId(), "each builder call mints a fresh appId");
    assertNotEquals(a.getUsername(), b.getUsername(), "each builder call mints a unique default username");
  }

  @Test
  public void userBuilderHonoursOverrides() {
    User user = aUser()
      .username("alice")
      .firstName("Alice")
      .lastName("Wonder")
      .email("alice@example.com")
      .orcid("0000-0001-2345-6789")
      .displayName("Alice W.")
      .build();
    assertEquals("alice", user.getUsername());
    assertEquals("Alice", user.getFirstName());
    assertEquals("Wonder", user.getLastName());
    assertEquals("alice@example.com", user.getEmail());
    assertEquals("0000-0001-2345-6789", user.getOrcid());
    assertEquals("Alice W.", user.getDisplayName());
  }

  // ---------- Collection ----------

  @Test
  public void collectionDefaultsAreSensible() {
    Collection collection = aCollection().build();

    assertNotNull(collection.getId(), "id must default to a non-null value");
    assertNotNull(collection.getShepardId(), "shepardId must default (mirroring id) so DAO stubs work");
    assertNotNull(collection.getName(), "name must default to a non-null value");
    assertFalse(collection.getName().isEmpty(), "name default must be non-empty");
    assertNotNull(collection.getAppId(), "appId must default to a fresh UUID");
    assertNotNull(collection.getAttributes(), "attributes must default to an empty map, not null");
    assertTrue(collection.getAttributes().isEmpty(), "attributes default empty");
    assertNotNull(collection.getCreatedAt(), "createdAt must default to a non-null value");
    assertFalse(collection.isDeleted(), "deleted must default to false");
  }

  @Test
  public void collectionBuilderProducesFreshAppIdAndIdPerCall() {
    Collection a = aCollection().build();
    Collection b = aCollection().build();
    assertNotEquals(a.getAppId(), b.getAppId());
    assertNotEquals(a.getId(), b.getId());
    assertNotEquals(a.getShepardId(), b.getShepardId());
  }

  @Test
  public void collectionBuilderHonoursOverrides() {
    User owner = aUser().username("bob").build();
    Date stamp = new Date(123L);
    Collection collection = aCollection()
      .id(42L)
      .shepardId(420L)
      .named("My data")
      .withDescription("a desc")
      .ownedBy(owner)
      .createdAt(stamp)
      .deleted(true)
      .build();
    assertEquals(42L, collection.getId());
    assertEquals(420L, collection.getShepardId());
    assertEquals("My data", collection.getName());
    assertEquals("a desc", collection.getDescription());
    assertSame(owner, collection.getCreatedBy());
    assertEquals(stamp, collection.getCreatedAt());
    assertTrue(collection.isDeleted());
  }

  // ---------- DataObject ----------

  @Test
  public void dataObjectDefaultsAreSensible() {
    DataObject dataObject = aDataObject().build();

    assertNotNull(dataObject.getId());
    assertNotNull(dataObject.getShepardId());
    assertNotNull(dataObject.getName());
    assertFalse(dataObject.getName().isEmpty());
    assertNotNull(dataObject.getAppId());
    assertNotNull(dataObject.getAttributes());
    assertTrue(dataObject.getAttributes().isEmpty());
    assertNotNull(dataObject.getCreatedAt());
    assertFalse(dataObject.isDeleted());
    assertNull(dataObject.getCollection(), "collection is wired explicitly via .inCollection(...)");
    assertNull(dataObject.getParent(), "parent stays null until explicitly set");
    assertNotNull(dataObject.getPredecessors(), "predecessors must default to an empty list, not null");
    assertTrue(dataObject.getPredecessors().isEmpty());
  }

  @Test
  public void dataObjectBuilderHonoursParentAndCollection() {
    Collection collection = aCollection().build();
    DataObject parent = aDataObject().inCollection(collection).build();
    DataObject child = aDataObject().inCollection(collection).withParent(parent).build();
    assertSame(collection, child.getCollection());
    assertSame(parent, child.getParent());
  }

  // ---------- Permissions ----------

  @Test
  public void permissionsDefaultsAreSensible() {
    Collection collection = aCollection().build();
    User owner = aUser().username("bob").build();
    Permissions permissions = permissionsFor(collection).ownedBy(owner).build();

    assertEquals(PermissionType.Private, permissions.getPermissionType(), "default permission type is Private");
    assertSame(owner, permissions.getOwner());
    assertNotNull(permissions.getEntities(), "entities list wraps the target entity");
    assertEquals(1, permissions.getEntities().size());
    assertSame(collection, permissions.getEntities().get(0));
    assertNotNull(permissions.getReader());
    assertNotNull(permissions.getWriter());
    assertNotNull(permissions.getManager());
    assertNotNull(permissions.getAppId(), "appId must default to a fresh UUID");
  }

  @Test
  public void permissionsBuilderAccumulatesReaderWriterManager() {
    Collection collection = aCollection().build();
    User owner = aUser().username("bob").build();
    User reader = aUser().username("rita").build();
    User writer = aUser().username("walt").build();
    User manager = aUser().username("mary").build();
    Permissions permissions = permissionsFor(collection)
      .ownedBy(owner)
      .type(PermissionType.Public)
      .reader(reader)
      .writer(writer)
      .manager(manager)
      .build();
    assertEquals(PermissionType.Public, permissions.getPermissionType());
    assertEquals(1, permissions.getReader().size());
    assertSame(reader, permissions.getReader().get(0));
    assertEquals(1, permissions.getWriter().size());
    assertSame(writer, permissions.getWriter().get(0));
    assertEquals(1, permissions.getManager().size());
    assertSame(manager, permissions.getManager().get(0));
  }

  // ---------- BasicReference ----------

  @Test
  public void basicReferenceDefaultsAreSensible() {
    BasicReference reference = aBasicReference().build();

    assertNotNull(reference.getId());
    assertNotNull(reference.getShepardId());
    assertNotNull(reference.getName());
    assertFalse(reference.getName().isEmpty());
    assertNotNull(reference.getAppId());
    assertNotNull(reference.getCreatedAt());
    assertFalse(reference.isDeleted());
    assertNull(reference.getDataObject(), "dataObject wired explicitly via .onDataObject(...)");
  }

  @Test
  public void basicReferenceBuilderHonoursDataObjectWiring() {
    DataObject dataObject = aDataObject().build();
    BasicReference reference = aBasicReference().onDataObject(dataObject).build();
    assertSame(dataObject, reference.getDataObject());
  }

  @Test
  public void nextIdIsStrictlyMonotonic() {
    long a = ShepardTestFixtures.nextId();
    long b = ShepardTestFixtures.nextId();
    long c = ShepardTestFixtures.nextId();
    assertTrue(b > a);
    assertTrue(c > b);
  }
}
