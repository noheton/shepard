package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PKIHelperTest extends BaseTestCase {

  @Mock
  private Path keysDir;

  @InjectMocks
  private PKIHelper pkiHelper;

  @Test
  public void testInit() {
    // TODO: How to test filesystem interactions?
    assertTrue(true);
  }
}
