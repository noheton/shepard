package de.dlr.shepard.common.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class InvalidAuthExceptionTest {

  @Test
  public void testDefaultConstructor() {
    var obj = new InvalidAuthException();
    assertEquals("Invalid authentication or authorization", obj.getMessage());
  }

  @Test
  public void testConstructor() {
    var obj = new InvalidAuthException("Message");
    assertEquals("Message", obj.getMessage());
  }

  @Test
  public void testGetStatusCode() {
    var obj = new InvalidAuthException();
    assertEquals(403, obj.getResponse().getStatus());
  }
}
