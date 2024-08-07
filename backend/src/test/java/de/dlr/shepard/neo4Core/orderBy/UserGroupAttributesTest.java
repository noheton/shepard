package de.dlr.shepard.neo4Core.orderBy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class UserGroupAttributesTest extends BaseTestCase {

  @Test
  public void isStringTest() {
    assertTrue(UserGroupAttributes.name.isString());
    assertFalse(UserGroupAttributes.createdAt.isString());
  }
}
