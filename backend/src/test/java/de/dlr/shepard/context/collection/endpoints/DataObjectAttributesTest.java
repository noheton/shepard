package de.dlr.shepard.context.collection.endpoints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DataObjectAttributesTest {

  @Test
  public void isStringTest() {
    assertTrue(DataObjectAttributes.name.isString());
    assertFalse(DataObjectAttributes.createdAt.isString());
  }
}
