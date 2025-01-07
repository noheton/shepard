package de.dlr.shepard.common.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ShepardProcessingExceptionTest {

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
