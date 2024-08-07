package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class PaginationHelperTest extends BaseTestCase {

  @Test
  public void getOffsetTest() {
    var helper = new PaginationHelper(10, 20);
    var actual = helper.getOffset();
    assertEquals(200, actual);
  }
}
