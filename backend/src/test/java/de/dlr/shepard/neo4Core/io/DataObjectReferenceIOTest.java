package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import de.dlr.shepard.neo4Core.entities.User;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class DataObjectReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(DataObjectReferenceIO.class).verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(33L);
    var referenced = new DataObject(3L);
    referenced.setShepardId(986L);

    var obj = new DataObjectReference(1L);
    obj.setShepardId(556L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setDataObject(dataObject);
    obj.setReferencedDataObject(referenced);
    obj.setRelationship("TestRel");

    var converted = new DataObjectReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(referenced.getShepardId(), converted.getReferencedDataObjectId());
    assertEquals(obj.getRelationship(), converted.getRelationship());
  }

  @Test
  public void testConversion_Deleted() {
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(195L);

    var obj = new DataObjectReference(1L);
    obj.setShepardId(58585L);
    obj.setDataObject(dataObject);
    obj.setReferencedDataObject(null);
    obj.setRelationship("TestRel");

    var converted = new DataObjectReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(-1, converted.getReferencedDataObjectId());
    assertEquals(obj.getRelationship(), converted.getRelationship());
  }
}
