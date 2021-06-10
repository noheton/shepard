package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.StructuredData;
import nl.jqno.equalsverifier.EqualsVerifier;

public class StructuredDataContainerTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(StructuredDataContainer.class)
				.withPrefabValues(User.class, new User("bob"), new User("claus")).verify();
	}

	@Test
	public void addDataTest() {
		var ref = new StructuredDataReference(1L);
		var structuredData = new StructuredData("oid");
		ref.addStructuredData(structuredData);

		assertEquals(ref.getStructuredDatas(), List.of(structuredData));
	}

}
