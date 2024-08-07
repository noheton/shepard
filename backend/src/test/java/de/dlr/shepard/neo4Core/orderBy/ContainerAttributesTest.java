package de.dlr.shepard.neo4Core.orderBy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class ContainerAttributesTest extends BaseTestCase {

  @Test
  public void isStringTest() {
    assertTrue(ContainerAttributes.name.isString());
    assertFalse(ContainerAttributes.createdAt.isString());
  }
}
