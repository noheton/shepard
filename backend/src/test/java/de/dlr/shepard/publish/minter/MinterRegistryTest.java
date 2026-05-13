package de.dlr.shepard.publish.minter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * KIP1h — exercises the relaxed {@link MinterRegistry}. Pre-KIP1h
 * the registry fail-fasted on missing / mismatched minters; post-KIP1h
 * every adapter lives in a plugin so the registry degrades cleanly
 * to "no active minter" + a WARN, letting the resolver keep working
 * for pre-existing rows. These tests pin the four optional-posture
 * outcomes (match / unset-or-none / no-bean / disabled-bean) plus
 * the duplicate-id and blank-id corner cases.
 */
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
    Minter local = fake("local", true);
    Minter epic = fake("epic", true);
    MinterRegistry r = new MinterRegistry("epic", instanceOf(local, epic));

    Optional<Minter> active = r.activeMinter();
    assertTrue(active.isPresent());
    assertSame(epic, active.get());
    assertEquals("epic", r.activeMinterId());
  }

  @Test
  void resolvesLocalMinterByDefaultWhenItMatches() {
    Minter local = fake("local", true);
    MinterRegistry r = new MinterRegistry("local", instanceOf(local));

    assertTrue(r.activeMinter().isPresent());
    assertEquals("local", r.activeMinterId());
  }

  // ---------- KIP1h optional-posture (no fail-fast) ----------

  @Test
  void unsetConfigDegradesToNoActiveMinter() {
    // Empty configured id (operator never set shepard.publish.minter) —
    // the registry now boots cleanly with no active minter, ready to
    // emit 503 from PublishService.
    Minter local = fake("local", true);
    MinterRegistry r = new MinterRegistry("", instanceOf(local));
    assertFalse(r.activeMinter().isPresent());
    assertEquals("<unset>", r.activeMinterId());
  }

  @Test
  void nullConfigDegradesToNoActiveMinter() {
    Minter local = fake("local", true);
    MinterRegistry r = new MinterRegistry(null, instanceOf(local));
    assertFalse(r.activeMinter().isPresent());
  }

  @Test
  void noneSentinelDegradesToNoActiveMinter() {
    // shepard.publish.minter=none is the operator-explicit "disable
    // publish" toggle. Lets an operator hand-shape the resolver-only
    // posture without uninstalling the minter plugin JAR.
    Minter local = fake("local", true);
    MinterRegistry r1 = new MinterRegistry("none", instanceOf(local));
    MinterRegistry r2 = new MinterRegistry("NONE", instanceOf(local));
    MinterRegistry r3 = new MinterRegistry("  NoNe  ", instanceOf(local));
    assertFalse(r1.activeMinter().isPresent());
    assertFalse(r2.activeMinter().isPresent());
    assertFalse(r3.activeMinter().isPresent());
  }

  @Test
  void configuredIdWithNoMatchingBeanDegradesNotFailFast() {
    // Pre-KIP1h this threw IllegalStateException. Post-KIP1h the
    // registry logs a WARN and continues with no active minter so
    // the resolver still works.
    Minter local = fake("local", true);
    MinterRegistry r = new MinterRegistry("epic", instanceOf(local));
    assertFalse(r.activeMinter().isPresent());
    assertEquals("<unset>", r.activeMinterId());
  }

  @Test
  void disabledActiveMinterDegradesNotFailFast() {
    // Pre-KIP1h this also threw. Post-KIP1h same WARN posture as the
    // missing-bean case.
    Minter disabled = fake("epic", false);
    MinterRegistry r = new MinterRegistry("epic", instanceOf(disabled));
    assertFalse(r.activeMinter().isPresent());
  }

  @Test
  void emptyMintersList() {
    // No plugins on the classpath at all — bare boot. Registry boots,
    // no active minter, ready to 503.
    MinterRegistry r = new MinterRegistry("local", instanceOf());
    assertFalse(r.activeMinter().isPresent());
  }

  // ---------- duplicate / blank id corner cases ----------

  @Test
  void duplicateIdsKeepsFirstAndWarns() {
    // Two minters return id "local". The registry keeps the first one
    // and logs a WARN (not a throw) — the plugin-first shape means an
    // operator may legitimately have a stock + forked variant on the
    // classpath; first-wins matches G1's host-substring behaviour.
    Minter first = fake("local", true);
    Minter second = fake("local", true);
    MinterRegistry r = new MinterRegistry("local", instanceOf(first, second));
    assertSame(first, r.activeMinter().orElseThrow());
  }

  @Test
  void minterReturningBlankIdIsSkipped() {
    Minter blank = fake("", true);
    Minter local = fake("local", true);
    MinterRegistry r = new MinterRegistry("local", instanceOf(blank, local));
    assertEquals("local", r.activeMinterId());
  }

  @Test
  void multipleMintersPickConfiguredOne() {
    // Realistic future scenario: ePIC + DataCite + local all wired,
    // operator picks one via shepard.publish.minter. The non-picked
    // beans stay discovered but inactive.
    Minter local = fake("local", true);
    Minter epic = fake("epic", true);
    Minter datacite = fake("datacite", true);
    MinterRegistry r = new MinterRegistry("datacite", instanceOf(local, epic, datacite));
    assertSame(datacite, r.activeMinter().orElseThrow());
    assertEquals("datacite", r.activeMinterId());
  }

  @Test
  void noneSentinelExposedAsConstant() {
    // Pin the sentinel for cross-module references (the CLI's
    // `shepard-admin publish status` follow-up would consult it).
    assertEquals("none", MinterRegistry.NONE);
  }
}
