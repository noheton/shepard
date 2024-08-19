package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class SemanticAnnotationIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(SemanticAnnotationIO.class).verify();
  }

  @Test
  public void testConversion() {
    var propertyRepository = new SemanticRepository(3L);
    var valueRepository = new SemanticRepository(4L);

    var obj = new SemanticAnnotation(1L);
    obj.setName("MyName");
    obj.setPropertyRepository(propertyRepository);
    obj.setValueRepository(valueRepository);
    obj.setPropertyIRI("prop");
    obj.setValueIRI("val");

    var converted = new SemanticAnnotationIO(obj);
    assertEquals(obj.getId(), converted.getId());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(3L, converted.getPropertyRepositoryId());
    assertEquals(4L, converted.getValueRepositoryId());
    assertEquals("prop", converted.getPropertyIRI());
    assertEquals("val", converted.getValueIRI());
  }

  @Test
  public void testConversion_RepositoryNull() {
    var obj = new SemanticAnnotation(1L);
    obj.setName("MyName");
    obj.setPropertyIRI("prop");
    obj.setValueIRI("val");

    var converted = new SemanticAnnotationIO(obj);
    assertEquals(obj.getId(), converted.getId());
    assertEquals(obj.getName(), converted.getName());
    assertEquals(-1, converted.getPropertyRepositoryId());
    assertEquals(-1, converted.getValueRepositoryId());
    assertEquals("prop", converted.getPropertyIRI());
    assertEquals("val", converted.getValueIRI());
  }

  @Test
  public void testUniqueId() {
    var obj = new SemanticAnnotationIO();
    obj.setId(123L);

    var actual = obj.getUniqueId();

    assertEquals("123", actual);
  }
}
