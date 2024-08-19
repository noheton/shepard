package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class PKIHelperTest {

  @InjectMock
  Path keysDir;

  @Inject
  PKIHelper pkiHelper;

  @Test
  public void testInit() {
    // TODO: How to test filesystem interactions?
    assertTrue(true);
  }
}
