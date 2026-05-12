package de.dlr.shepard.context.references.file.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the FR1b singleton {@link FileReference} entity
 * (see {@code aidocs/53 §1.8.6}).
 *
 * <p>Pin the label-discriminator + getType-string shape that lets
 * the upstream-frozen API and the new /v2/files surface coexist
 * without ambiguity.
 */
class FileReferenceTest {

  @Test
  void getType_returnsFileReferenceString() {
    // Both FR1a bundles and FR1b singletons expose getType() ==
    // "FileReference" so the upstream-API serialiser projects both
    // onto the same legacy wire shape (aidocs/53 §1.8.4).
    var singleton = new FileReference();
    assertEquals("FileReference", singleton.getType());
  }

  @Test
  void getType_matchesBundle() {
    var singleton = new FileReference();
    var bundle = new FileBundleReference();
    assertEquals(bundle.getType(), singleton.getType());
  }

  @Test
  void equalsAndHashCode_distinguishOnFile() {
    var a = new FileReference(1L);
    a.setAppId("same-app-id");
    var b = new FileReference(1L);
    b.setAppId("same-app-id");

    // identical empty rows → equal
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    var file = new ShepardFile(new Date(), "doc.pdf", "deadbeef");
    file.setOid("file-1");
    a.setFile(file);
    // Adding file to one side breaks equality
    assertNotEquals(a, b);
  }

  @Test
  void equalsHandlesNullFile() {
    var a = new FileReference(1L);
    a.setAppId("a");
    var b = new FileReference(1L);
    b.setAppId("a");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void selfEqualityAndNullRejection() {
    var a = new FileReference(1L);
    assertEquals(a, a);
    assertNotEquals(a, null);
    assertNotEquals(a, "not a FileReference");
  }

  @Test
  void inheritsDataObjectRelationship() {
    // Sanity check that singletons participate in the cross-Reference
    // (d)-[:has_reference]->(r) traversal via the inherited BasicReference
    // dataObject field. This is the runtime invariant §1.8.3 calls out
    // ("Both share the same :Reference label so the existing has_reference
    // traversal at the DataObject level remains uniform").
    var singleton = new FileReference();
    var parent = new DataObject(42L);
    parent.setAppId("do-app");
    singleton.setDataObject(parent);
    assertEquals(parent, singleton.getDataObject());
  }

  @Test
  void labelAnnotationIsSingletonFileReference() {
    // The label discriminator is the structural lever that keeps the
    // FR1a bundle DAO (which queries :FileReference) from
    // accidentally picking up singletons. Pin it.
    var ann = FileReference.class.getAnnotation(org.neo4j.ogm.annotation.NodeEntity.class);
    assertNotNull(ann);
    assertEquals("SingletonFileReference", ann.label());
  }
}
