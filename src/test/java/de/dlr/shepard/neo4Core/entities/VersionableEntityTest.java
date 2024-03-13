package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

public class VersionableEntityTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(VersionableEntity.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
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

}
