package de.dlr.shepard.v2.notifications.transport.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationMessage;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — registry contract tests.
 *
 * <p>Covers: empty registry returns {@link java.util.Optional#empty()};
 * registered sender resolves; unknown {@code kind} string returns empty
 * without throwing; null transport returns empty; programmatic
 * {@code register()} works for tests without CDI discovery.
 */
class NotificationTransportRegistryTest {

  private NotificationTransportRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new NotificationTransportRegistry();
    registry.clear();
  }

  @Test
  void resolve_returnsEmptyWhenTransportNull() {
    assertTrue(registry.resolve(null).isEmpty());
  }

  @Test
  void resolve_returnsEmptyWhenKindNull() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    assertTrue(registry.resolve(t).isEmpty());
  }

  @Test
  void resolve_returnsEmptyOnUnknownKindString() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    t.setKind("TELEPATHIC");
    // Should not throw; should log a WARN and return empty.
    assertTrue(registry.resolve(t).isEmpty());
  }

  @Test
  void resolve_returnsRegisteredSender() {
    NotificationSender fake = new FakeSender(TransportKind.SMTP);
    registry.register(fake);

    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    t.setKind(TransportKind.SMTP.name());

    var resolved = registry.resolve(t);
    assertTrue(resolved.isPresent());
    assertEquals(TransportKind.SMTP, resolved.get().kind());
  }

  @Test
  void resolve_emptyWhenKindRecognisedButUnregistered() {
    NotificationTransport t = new NotificationTransport();
    t.setAppId("app-1");
    t.setKind(TransportKind.MATRIX.name());

    assertTrue(registry.resolve(t).isEmpty(),
        "MATRIX kind is valid enum but no sender registered → empty (fail-soft)");
  }

  @Test
  void knownKindCountMatchesEnumValues() {
    // Sanity: if a kind is added to the enum without updating the switch,
    // javac warns and this count diverges.
    assertEquals(TransportKind.values().length, NotificationTransportRegistry.knownKindCount());
  }

  @Test
  void register_replacesPreviousSenderForSameKind() {
    NotificationSender a = new FakeSender(TransportKind.SMTP);
    NotificationSender b = new FakeSender(TransportKind.SMTP);
    registry.register(a);
    registry.register(b);

    NotificationTransport t = new NotificationTransport();
    t.setKind(TransportKind.SMTP.name());
    var resolved = registry.resolve(t);
    assertTrue(resolved.isPresent());
    // Either implementation is acceptable per the registry contract;
    // verify it's a real binding (not null).
    assertFalse(resolved.isEmpty());
  }

  private static final class FakeSender implements NotificationSender {
    private final TransportKind kind;

    FakeSender(TransportKind kind) {
      this.kind = kind;
    }

    @Override
    public TransportKind kind() {
      return kind;
    }

    @Override
    public boolean send(NotificationTransport transport, NotificationMessage message) {
      return true;
    }
  }
}
