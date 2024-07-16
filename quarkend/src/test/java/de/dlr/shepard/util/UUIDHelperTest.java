package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.BaseTestCase;
import org.junit.jupiter.api.Test;

public class UUIDHelperTest extends BaseTestCase {

  private UUIDHelper helper = new UUIDHelper();

  @Test
  public void getUUIDTest() {
    var actual = helper.getUUID();
    assertNotNull(actual);
  }
}
