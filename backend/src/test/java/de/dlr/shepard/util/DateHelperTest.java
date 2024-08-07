package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class DateHelperTest extends BaseTestCase {

  private DateHelper helper = new DateHelper();

  @Test
  public void getDateTest() {
    var actual = helper.getDate();
    assertNotNull(actual);
  }
}
