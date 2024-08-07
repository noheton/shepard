package de.dlr.shepard.exceptions;

import de.dlr.shepard.BaseTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ApiErrorTest extends BaseTestCase {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ApiError.class).verify();
  }
}
