package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class ShepardProcessingExceptionTest extends BaseTestCase {

  @Test
  public void testConstructor() {
    var obj = new ShepardProcessingException("Message");
    assertEquals("Message", obj.getMessage());
  }

  @Test
  public void testGetStatusCode() {
    var obj = new ShepardProcessingException("");
    assertEquals(500, obj.getResponse().getStatus());
  }
}
