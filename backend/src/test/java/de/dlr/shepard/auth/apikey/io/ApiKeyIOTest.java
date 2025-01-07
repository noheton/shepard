package de.dlr.shepard.auth.apikey.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import java.util.Date;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ApiKeyIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ApiKeyIO.class).verify();
  }

  @Test
  public void testConversion() {
    var user = new User("bob");
    var date = new Date();
    var key = new ApiKey(UUID.randomUUID());
    key.setBelongsTo(user);
    key.setCreatedAt(date);
    key.setJws("MyJWS");
    key.setName("MyKey");

    var converted = new ApiKeyIO(key);
    assertEquals(user.getUsername(), converted.getBelongsTo());
    assertEquals(date, converted.getCreatedAt());
    assertEquals("MyKey", converted.getName());
    assertEquals(key.getUid(), converted.getUid());
  }

  @Test
  public void testConversionNoUser() {
    var key = new ApiKey(UUID.randomUUID());

    var converted = new ApiKeyIO(key);
    assertNull(converted.getBelongsTo());
  }
}
