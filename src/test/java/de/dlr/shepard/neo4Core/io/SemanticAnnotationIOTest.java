package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.neo4Core.entities.SemanticRepository;
import de.dlr.shepard.neo4Core.entities.User;
import nl.jqno.equalsverifier.EqualsVerifier;

public class SemanticAnnotationIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(SemanticAnnotationIO.class).verify();
	}

	@Test
	public void testConversion() {
		var date = new Date();
		var user = new User("bob");
		var update = new Date();
		var updateUser = new User("claus");
		var propertyRepository = new SemanticRepository(3L);
		var valueRepository = new SemanticRepository(4L);

		var obj = new SemanticAnnotation(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setUpdatedAt(update);
		obj.setUpdatedBy(updateUser);
		obj.setPropertyRepository(propertyRepository);
		obj.setValueRepository(valueRepository);
		obj.setPropertyIRI("prop");
		obj.setValueIRI("val");

		var converted = new SemanticAnnotationIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(obj.getUpdatedAt(), converted.getUpdatedAt());
		assertEquals("claus", converted.getUpdatedBy());
		assertEquals(3L, converted.getPropertyRepositoryId());
		assertEquals(4L, converted.getValueRepositoryId());
		assertEquals("prop", converted.getPropertyIRI());
		assertEquals("val", converted.getValueIRI());
	}

	@Test
	public void testConversion_RepositoryNull() {
		var date = new Date();
		var user = new User("bob");

		var obj = new SemanticAnnotation(1L);
		obj.setCreatedAt(date);
		obj.setCreatedBy(user);
		obj.setName("MyName");
		obj.setPropertyIRI("prop");
		obj.setValueIRI("val");

		var converted = new SemanticAnnotationIO(obj);
		assertEquals(obj.getId(), converted.getId());
		assertEquals(obj.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(obj.getName(), converted.getName());
		assertEquals(-1, converted.getPropertyRepositoryId());
		assertEquals(-1, converted.getValueRepositoryId());
		assertEquals("prop", converted.getPropertyIRI());
		assertEquals("val", converted.getValueIRI());
	}

}
