package de.dlr.shepard.context.references.file.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import java.util.Date;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class FileBundleReferenceTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(FileBundleReference.class)
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(Version.class, new Version("Version1"), new Version("Version2"))
      .withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .withPrefabValues(FileContainer.class, new FileContainer(2L), new FileContainer(3L))
      .withPrefabValues(FileGroup.class, new FileGroup(1L), new FileGroup(2L))
      // appId is L2a-additive; not part of equals (legacy id remains canonical).
      // groups (FR1a) and extraLabels (the @Labels marker for FR1a) are
      // navigational / persistence-shape fields, not equality-defining.
      .withIgnoredFields("appId", "groups", "extraLabels")
      .verify();
  }

  @Test
  public void addFilesTest() {
    var ref = new FileBundleReference(1L);
    var file = new ShepardFile("oid", new Date(), "name", "md5");
    ref.addFile(file);

    assertEquals(List.of(file), ref.getFiles());
  }

  @Test
  public void addGroupTest() {
    var ref = new FileBundleReference(1L);
    var group = new FileGroup(2L);
    group.setName("default");
    ref.addGroup(group);

    assertEquals(List.of(group), ref.getGroups());
  }

  /**
   * FR1a — the {@code @Labels}-annotated {@code extraLabels} field must
   * always include {@code "FileBundleReference"} so freshly-saved
   * entities pick up the new label alongside the legacy
   * {@code :FileReference} label declared via {@code @NodeEntity}.
   * This is the test the design doc asks for in §1.7.
   */
  @Test
  public void newInstance_carriesFileBundleReferenceExtraLabel() {
    var ref = new FileBundleReference();
    assertEquals(List.of("FileBundleReference"), ref.getExtraLabels(),
      "every fresh FileBundleReference must persist with both :FileReference (from @NodeEntity) " +
      "and :FileBundleReference (from @Labels) labels");
  }

  /**
   * FR1a — {@code getType()} is pinned to {@code "FileReference"} for
   * upstream-API wire compatibility. The ExportService and
   * EntityUrlSynthesiser switch logic + the BasicReferenceIO {@code type}
   * field surface this string; clients keyed off it would break if
   * the rename leaked through.
   */
  @Test
  public void getType_returnsLegacyFileReferenceForWireCompat() {
    var ref = new FileBundleReference();
    assertEquals("FileReference", ref.getType(),
      "getType() must remain 'FileReference' for upstream API wire compatibility (see CLAUDE.md)");
  }
}
