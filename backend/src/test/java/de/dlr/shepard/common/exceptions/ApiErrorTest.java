package de.dlr.shepard.common.exceptions;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ApiErrorTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ApiError.class).verify();
  }
}
