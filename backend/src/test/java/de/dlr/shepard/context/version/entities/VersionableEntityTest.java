package de.dlr.shepard.context.version.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class VersionableEntityTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(VersionableEntity.class)
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(Version.class, new Version("Version1"), new Version("Version2"))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      // appId and revision are server-managed metadata; not part of identity equals.
      .withIgnoredFields("appId", "revision")
      .verify();
  }

  @Test
  public void addAnnotationTest() {
    var annotation2 = new SemanticAnnotation(2L);
    var annotation3 = new SemanticAnnotation(3L);
    var entity = new Collection(1L);
    entity.addAnnotation(annotation2);
    entity.addAnnotation(annotation3);
    assertEquals(List.of(annotation2, annotation3), entity.getAnnotations());
  }

  @Test
  public void newEntityHasRevisionOne() {
    // V2a: every fresh VersionableEntity starts at revision=1.
    var entity = new Collection();
    assertEquals(1L, entity.getRevision(), "New entity must start at revision 1");
  }

  @Test
  public void revisionIncrementTest() {
    // V2a: simulate the DAO write-side increment on an entity that already has an id.
    var entity = new Collection(42L);
    assertEquals(1L, entity.getRevision(), "Before any update the revision must be 1");
    entity.setRevision(entity.getRevision() + 1);
    assertEquals(2L, entity.getRevision(), "After one increment the revision must be 2");
    entity.setRevision(entity.getRevision() + 1);
    assertEquals(3L, entity.getRevision(), "After two increments the revision must be 3");
  }
}
