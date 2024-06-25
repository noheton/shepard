package de.dlr.shepard.neo4Core.orderBy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class BasicReferenceAttributesTest extends BaseTestCase {

	@Test
	public void isStringTest() {
		assertTrue(BasicReferenceAttributes.name.isString());
		assertFalse(BasicReferenceAttributes.createdAt.isString());
	}
}
