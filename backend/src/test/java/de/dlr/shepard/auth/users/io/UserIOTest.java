package de.dlr.shepard.auth.users.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.subscription.entities.Subscription;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class UserIOTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(UserIO.class).verify();
  }

  /**
   * Regression for task #131 (v5 wire-fidelity audit). {@code orcid} and
   * {@code displayName} are fork additions (U1a / U1b) — upstream 5.2.0
   * has no such keys on {@code UserIO}. Per
   * {@code CLAUDE.md §"API-version policy"} they must be omitted from
   * v1 wire when null. The {@code @JsonInclude(NON_NULL)} on the fields
   * is the fix; this test pins it at the serialisation boundary.
   */
  @Test
  public void orcidAndDisplayName_areOmittedFromJson_whenNull() throws Exception {
    var io = new UserIO();
    io.setUsername("bob");
    // orcid + displayName left null (the default for users that haven't opted in)
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).doesNotContain("orcid");
    assertThat(json).doesNotContain("displayName");
  }

  @Test
  public void orcid_isSerialised_whenSet() throws Exception {
    var io = new UserIO();
    io.setUsername("bob");
    io.setOrcid("0000-0002-1825-0097");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"orcid\":\"0000-0002-1825-0097\"");
  }

  @Test
  public void displayName_isSerialised_whenSet() throws Exception {
    var io = new UserIO();
    io.setUsername("bob");
    io.setDisplayName("Bob (Test)");
    String json = new ObjectMapper().writeValueAsString(io);
    assertThat(json).contains("\"displayName\":\"Bob (Test)\"");
  }

  @Test
  public void testConversion() {
    var key = new ApiKey(UUID.randomUUID());
    var sub = new Subscription(2L);

    var user = new User("bob");
    user.setApiKeys(List.of(key));
    user.setEmail("Email");
    user.setFirstName("name");
    user.setLastName("last");
    user.setSubscriptions(List.of(sub));

    var converted = new UserIO(user);
    assertEquals(user.getUsername(), converted.getUsername());
    assertEquals("[%s]".formatted(key.getUid()), Arrays.toString(converted.getApiKeyIds()));
    assertEquals(user.getEmail(), converted.getEmail());
    assertEquals(user.getFirstName(), converted.getFirstName());
    assertEquals(user.getLastName(), converted.getLastName());
    assertEquals("[2]", Arrays.toString(converted.getSubscriptionIds()));
  }
}
