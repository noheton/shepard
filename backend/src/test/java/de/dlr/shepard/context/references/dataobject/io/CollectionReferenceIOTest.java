package de.dlr.shepard.context.references.dataobject.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class CollectionReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(CollectionReferenceIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(4L);
    var referenced = new Collection(3L);
    referenced.setShepardId(5L);

    var obj = new CollectionReference(1L);
    obj.setShepardId(2L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setDataObject(dataObject);
    obj.setReferencedCollection(referenced);
    obj.setRelationship("TestRel");

    var converted = new CollectionReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(referenced.getShepardId(), converted.getReferencedCollectionId());
    assertEquals(obj.getRelationship(), converted.getRelationship());
  }

  @Test
  public void testConversion_Deleted() {
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(133L);

    var obj = new CollectionReference(1L);
    obj.setShepardId(33L);
    obj.setDataObject(dataObject);
    obj.setReferencedCollection(null);
    obj.setRelationship("TestRel");

    var converted = new CollectionReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(-1, converted.getReferencedCollectionId());
    assertEquals(obj.getRelationship(), converted.getRelationship());
  }
}
