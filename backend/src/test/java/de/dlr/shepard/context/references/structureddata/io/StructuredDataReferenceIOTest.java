package de.dlr.shepard.context.references.structureddata.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class StructuredDataReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(StructuredDataReferenceIO.class).verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(22L);
    var container = new StructuredDataContainer(3L);
    var structuredData = new StructuredData("oid", new Date(), "name");

    var obj = new StructuredDataReference(1L);
    obj.setShepardId(123L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setDataObject(dataObject);
    obj.setStructuredDataContainer(container);
    obj.setStructuredDatas(List.of(structuredData));
    String[] oids = obj.getStructuredDatas().stream().map(StructuredData::getOid).toArray(String[]::new);

    var converted = new StructuredDataReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(container.getId(), converted.getStructuredDataContainerId());
    assertTrue(Arrays.equals(oids, converted.getStructuredDataOids()));
  }

  @Test
  public void testConversion_ContainerNull() {
    var date = new Date();
    var user = new User("bob");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(123L);
    var structuredData = new StructuredData("oid", new Date(), "name");

    var obj = new StructuredDataReference(1L);
    obj.setShepardId(22L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setDataObject(dataObject);
    obj.setStructuredDatas(List.of(structuredData));
    String[] oids = obj.getStructuredDatas().stream().map(StructuredData::getOid).toArray(String[]::new);

    var converted = new StructuredDataReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(-1, converted.getStructuredDataContainerId());
    assertTrue(Arrays.equals(oids, converted.getStructuredDataOids()));
  }
}
