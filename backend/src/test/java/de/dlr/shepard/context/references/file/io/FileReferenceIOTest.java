package de.dlr.shepard.context.references.file.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class FileReferenceIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(FileReferenceIO.class).verify();
  }

  @Test
  public void testConversion() {
    var date = new Date();
    var user = new User("bob");
    var update = new Date();
    var updateUser = new User("claus");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(654L);
    var container = new FileContainer(3L);
    var file = new ShepardFile("oid", new Date(), "name", "md5");

    var obj = new FileReference(1L);
    obj.setShepardId(48L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setDataObject(dataObject);
    obj.setFileContainer(container);
    obj.setFiles(List.of(file));
    String[] oids = obj.getFiles().stream().map(ShepardFile::getOid).toArray(String[]::new);

    var converted = new FileReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(3L, converted.getFileContainerId());
    assertTrue(Arrays.equals(oids, converted.getFileOids()));
  }

  @Test
  public void testConversion_ContainerNull() {
    var date = new Date();
    var user = new User("bob");
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(734L);
    var file = new ShepardFile("oid", new Date(), "name", "md5");

    var obj = new FileReference(1L);
    obj.setShepardId(399L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setName("MyName");
    obj.setDataObject(dataObject);
    obj.setFiles(List.of(file));
    String[] oids = obj.getFiles().stream().map(ShepardFile::getOid).toArray(String[]::new);

    var converted = new FileReferenceIO(obj);
    assertEquals(obj.getShepardId(), converted.getId());
    assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(dataObject.getShepardId(), converted.getDataObjectId());
    assertEquals(-1, converted.getFileContainerId());
    assertTrue(Arrays.equals(oids, converted.getFileOids()));
  }
}
