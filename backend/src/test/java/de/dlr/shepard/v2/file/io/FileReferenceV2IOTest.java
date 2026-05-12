package de.dlr.shepard.v2.file.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the FR1b singleton wire shape (see
 * {@code aidocs/53 §1.8.6}). The IO copies fields from the entity at
 * construction time; this test pins the field set so a rename or
 * silent drop is caught.
 */
class FileReferenceV2IOTest {

  private FileReference newSingleton() {
    var ref = new FileReference(1L);
    ref.setAppId("singleton-app-1");
    ref.setName("doc");
    var parent = new DataObject(42L);
    parent.setAppId("do-app");
    parent.setShepardId(101L);
    ref.setDataObject(parent);
    var file = new ShepardFile(new Date(), "doc.pdf", "deadbeef");
    file.setOid("file-oid-1");
    file.setFileSize(1024L);
    ref.setFile(file);
    return ref;
  }

  @Test
  void copiesAppIdFromEntity() {
    var io = new FileReferenceV2IO(newSingleton());
    assertEquals("singleton-app-1", io.getAppId());
  }

  @Test
  void copiesFileFromEntity() {
    var io = new FileReferenceV2IO(newSingleton());
    assertNotNull(io.getFile());
    assertEquals("doc.pdf", io.getFile().getFilename());
    assertEquals(Long.valueOf(1024L), io.getFile().getFileSize());
  }

  @Test
  void typeStringIsFileReference() {
    // Inherited from BasicReferenceIO via FileReference.getType()
    // which is pinned to "FileReference" for upstream-API
    // compatibility (§1.8.4).
    var io = new FileReferenceV2IO(newSingleton());
    assertEquals("FileReference", io.getType());
  }

  @Test
  void noArgConstructorYieldsNullFields() {
    var io = new FileReferenceV2IO();
    assertNull(io.getAppId());
    assertNull(io.getFile());
  }

  @Test
  void equalityRespectsAppId() {
    var a = new FileReferenceV2IO(newSingleton());
    var b = new FileReferenceV2IO(newSingleton());
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    b.setAppId("different");
    assertNotEquals(a, b);
  }

  @Test
  void selfEqualityAndNullRejection() {
    var a = new FileReferenceV2IO(newSingleton());
    assertEquals(a, a);
    assertNotEquals(a, null);
    assertNotEquals(a, "not an IO");
  }

  @Test
  void equalityRespectsFile() {
    var a = new FileReferenceV2IO(newSingleton());
    var b = new FileReferenceV2IO(newSingleton());
    b.setFile(null);
    assertNotEquals(a, b);
  }
}
