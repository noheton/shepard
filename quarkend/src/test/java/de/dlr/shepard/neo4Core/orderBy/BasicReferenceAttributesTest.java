package de.dlr.shepard.neo4Core.orderBy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class BasicReferenceAttributesTest extends BaseTestCase {

  @Test
  public void isStringTest() {
    assertTrue(BasicReferenceAttributes.name.isString());
    assertFalse(BasicReferenceAttributes.createdAt.isString());
  }
}
