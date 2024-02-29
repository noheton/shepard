package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataReferenceTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredDataReference.class)
				.withPrefabValues(DataObject.class, new DataObject(1L), new DataObject(2L))
				.withPrefabValues(User.class, new User("bob"), new User("claus"))
				.withPrefabValues(UserGroup.class, new UserGroup(1L), new UserGroup(2L))
				.withPrefabValues(SemanticAnnotation.class, new SemanticAnnotation(1L), new SemanticAnnotation(2L))
				.verify();
	}

	@Test
	public void addStructuredDataTest() {
		var ref = new StructuredDataReference(1L);
		var sd = new StructuredData("newOid", new Date(), "name");
		ref.addStructuredData(sd);

		assertEquals(List.of(sd), ref.getStructuredDatas());
	}

}
