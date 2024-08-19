package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PaginationHelperTest {

  @Test
  public void getOffsetTest() {
    var helper = new PaginationHelper(10, 20);
    var actual = helper.getOffset();
    assertEquals(200, actual);
  }
}
