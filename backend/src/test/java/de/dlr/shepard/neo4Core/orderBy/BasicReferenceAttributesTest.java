package de.dlr.shepard.neo4Core.orderBy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BasicReferenceAttributesTest {

  @Test
  public void isStringTest() {
    assertTrue(BasicReferenceAttributes.name.isString());
    assertFalse(BasicReferenceAttributes.createdAt.isString());
  }
}
