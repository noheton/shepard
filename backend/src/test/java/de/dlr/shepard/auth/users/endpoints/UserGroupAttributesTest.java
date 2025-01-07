package de.dlr.shepard.auth.users.endpoints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class UserGroupAttributesTest {

  @Test
  public void isStringTest() {
    assertTrue(UserGroupAttributes.name.isString());
    assertFalse(UserGroupAttributes.createdAt.isString());
  }
}
