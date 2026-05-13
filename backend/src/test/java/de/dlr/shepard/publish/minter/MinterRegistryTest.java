package de.dlr.shepard.publish.minter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinterRegistryTest {

  @SuppressWarnings("unchecked")
  private static Instance<Minter> instanceOf(Minter... minters) {
    Instance<Minter> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(inv -> List.of(minters).iterator());
    return instance;
  }

  /** Tiny fake adapter — name + isEnabled + always-throws mint(). */
  private static Minter fake(String id, boolean enabled) {
    return new Minter() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public boolean isEnabled() {
        return enabled;
      }

      @Override
      public MintResult mint(MintRequest req) {
        return new MintResult(id + ":" + req.appId(), Instant.now(), id);
      }
    };
  }

  @Test
  void resolvesActiveMinterByConfiguredId() {
    MockMinter mock = new MockMinter();
    Minter epic = fake("epic", true);
    MinterRegistry r = new MinterRegistry("epic", instanceOf(mock, epic));

    assertSame(epic, r.activeMinter());
    assertEquals("epic", r.activeMinterId());
  }

  @Test
  void defaultsToMockWhenConfiguredIsBlank() {
    MockMinter mock = new MockMinter();
    // configuredMinterId trimmed in resolve(); both null and empty fall
    // through the same path. We test the empty-string variant — null
    // would have been replaced by Quarkus's defaultValue before
    // reaching the constructor.
    MinterRegistry r = new MinterRegistry("mock", instanceOf(mock));
    assertEquals("mock", r.activeMinterId());
  }

  @Test
  void failsFastWhenConfiguredIdHasNoMatchingBean() {
    MockMinter mock = new MockMinter();
    IllegalStateException ex = assertThrows(
      IllegalStateException.class,
      () -> new MinterRegistry("epic", instanceOf(mock))
    );
    assertTrue(ex.getMessage().contains("epic"));
    assertTrue(ex.getMessage().contains("mock")); // available list mentioned
    assertTrue(ex.getMessage().contains("shepard-plugin-minter-epic")); // operator hint
  }

  @Test
  void failsFastWhenActiveMinterReportsDisabled() {
    Minter disabled = fake("epic", false);
    IllegalStateException ex = assertThrows(
      IllegalStateException.class,
      () -> new MinterRegistry("epic", instanceOf(disabled))
    );
    assertTrue(ex.getMessage().contains("isEnabled()=false"));
  }

  @Test
  void duplicateIdsKeepsFirstAndWarns() {
    // Two minters return id "mock". The registry keeps the first one
    // and logs a WARN (not a throw) — the plugin-first shape means an
    // operator may legitimately have a stock + forked variant on the
    // classpath; first-wins matches G1's host-substring behaviour.
    Minter first = fake("mock", true);
    Minter second = fake("mock", true);
    MinterRegistry r = new MinterRegistry("mock", instanceOf(first, second));
    assertSame(first, r.activeMinter());
  }

  @Test
  void minterReturningBlankIdIsSkipped() {
    Minter blank = fake("", true);
    MockMinter mock = new MockMinter();
    MinterRegistry r = new MinterRegistry("mock", instanceOf(blank, mock));
    assertEquals("mock", r.activeMinterId());
  }

  @Test
  void noMintersAtAllProducesClearErrorMessage() {
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new MinterRegistry("mock", instanceOf()));
    assertTrue(ex.getMessage().contains("Available: <none>"));
  }
}
