package de.dlr.shepard.common.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class UUIDHelperTest {

  @Inject
  UUIDHelper helper;

  @Test
  public void getUUIDTest() {
    var actual = helper.getUUID();
    assertNotNull(actual);
  }
}
