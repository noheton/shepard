package de.dlr.shepard.auth.apikey.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ApiKeyWithJWTIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ApiKeyWithJWTIO.class).verify();
  }

  @Test
  public void testConversion() {
    var key = new ApiKey(UUID.randomUUID());
    key.setJws("MyJWS");

    var converted = new ApiKeyWithJWTIO(key);
    assertEquals(key.getUid(), converted.getUid());
    assertEquals("MyJWS", converted.getJwt());
  }
}
