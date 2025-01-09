package de.dlr.shepard.context.collection.endpoints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.ContainerAttributes;
import org.junit.jupiter.api.Test;

public class ContainerAttributesTest {

  @Test
  public void isStringTest() {
    assertTrue(ContainerAttributes.name.isString());
    assertFalse(ContainerAttributes.createdAt.isString());
  }
}
