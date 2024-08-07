package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class DataObjectTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple()
      .forClass(DataObject.class)
      .withPrefabValues(Collection.class, new Collection(1L), new Collection(2L))
      .withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
      .withPrefabValues(DataObjectReference.class, new DataObjectReference(1L), new DataObjectReference(2L))
      .withPrefabValues(BasicReference.class, new BasicReference(1L), new BasicReference(2L))
      .withPrefabValues(User.class, new User("bob"), new User("claus"))
      .withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
      .verify();
  }

  @Test
  public void addChildTest() {
    var dataObject = new DataObject(1L);
    var child = new DataObject(2L);
    dataObject.addChild(child);

    assertEquals(dataObject.getChildren(), List.of(child));
  }

  @Test
  public void addPredeccesorTest() {
    var dataObject = new DataObject(1L);
    var predecessor = new DataObject(2L);
    dataObject.addPredecessor(predecessor);

    assertEquals(dataObject.getPredecessors(), List.of(predecessor));
  }

  @Test
  public void addSuccessorTest() {
    var dataObject = new DataObject(1L);
    var successor = new DataObject(2L);
    dataObject.addSuccessor(successor);

    assertEquals(dataObject.getSuccessors(), List.of(successor));
  }

  @Test
  public void addReferenceTest() {
    var dataObject = new DataObject(1L);
    var ref = new BasicReference(2L);
    dataObject.addReference(ref);

    assertEquals(dataObject.getReferences(), List.of(ref));
  }

  @Test
  public void addIncomingTest() {
    var incoming = new DataObjectReference(2L);
    var dataObject = new DataObject(1L);

    dataObject.addIncoming(incoming);
    assertEquals(List.of(incoming), dataObject.getIncoming());
  }
}
