package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GracePeriodUtilTest {

  @Test
  public void elementIsKnownTest_False() {
    GracePeriodUtil util = new GracePeriodUtil(1000);
    assertFalse(util.elementIsKnown("Test"));
  }

  @Test
  public void elementIsKnownTest_True() {
    GracePeriodUtil util = new GracePeriodUtil(1000);
    util.elementSeen("Test");
    assertTrue(util.elementIsKnown("Test"));
  }

  @Test
  public void elementIsKnownTest_Outdated() throws InterruptedException {
    GracePeriodUtil util = new GracePeriodUtil(1);
    util.elementSeen("Test");
    Thread.sleep(2);
    assertFalse(util.elementIsKnown("Test"));
  }
}
