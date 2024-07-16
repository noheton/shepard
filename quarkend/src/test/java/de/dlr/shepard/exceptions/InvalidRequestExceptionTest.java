package de.dlr.shepard.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class InvalidRequestExceptionTest extends BaseTestCase {

  @Test
  public void testDefaultConstructor() {
    var obj = new InvalidRequestException();
    assertEquals("The request is incorrect and cannot be processed", obj.getMessage());
  }

  @Test
  public void testConstructor() {
    var obj = new InvalidRequestException("Message");
    assertEquals("Message", obj.getMessage());
  }

  @Test
  public void testGetStatusCode() {
    var obj = new InvalidRequestException();
    assertEquals(400, obj.getResponse().getStatus());
  }
}
