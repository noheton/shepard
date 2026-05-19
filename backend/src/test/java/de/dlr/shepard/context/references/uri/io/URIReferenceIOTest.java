package de.dlr.shepard.context.references.uri.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class URIReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(URIReferenceIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(4L);

    var obj = new URIReference(1L);
    obj.setShepardId(22L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setDataObject(dataObject);
    obj.setUri("http://abc.de");
    obj.setRelationship("test");

    var converted = new URIReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(obj.getUri(), converted.getUri());
    assertEquals(obj.getRelationship(), converted.getRelationship());
  }
}
