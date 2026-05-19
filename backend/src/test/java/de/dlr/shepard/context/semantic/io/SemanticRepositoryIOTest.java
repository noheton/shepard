package de.dlr.shepard.context.semantic.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class SemanticRepositoryIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(SemanticRepositoryIO.class).withIgnoredFields("revision").verify();
  }

  @Test
  public void testConversion() {
    var user = new User("bob");
    var date = new Date();
    var update = new Date();
    var updateUser = new User("claus");

    var obj = new SemanticRepository(1L);
    obj.setCreatedAt(date);
    obj.setCreatedBy(user);
    obj.setUpdatedAt(update);
    obj.setUpdatedBy(updateUser);
    obj.setName("name");
    obj.setEndpoint("sparql");
    obj.setType(SemanticRepositoryType.SPARQL);

    var converted = new SemanticRepositoryIO(obj);
    assertEquals(date, converted.getCreatedAt());
    assertEquals("bob", converted.getCreatedBy());
    assertEquals(update, converted.getUpdatedAt());
    assertEquals("claus", converted.getUpdatedBy());
    assertEquals(1L, converted.getId());
    assertEquals("name", converted.getName());
    assertEquals("sparql", converted.getEndpoint());
    assertEquals(SemanticRepositoryType.SPARQL, converted.getType());
  }
}
