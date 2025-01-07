package de.dlr.shepard.common.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class DateHelperTest {

  @Inject
  DateHelper helper;

  @Test
  public void getDateTest() {
    var actual = helper.getDate();
    assertNotNull(actual);
  }
}
