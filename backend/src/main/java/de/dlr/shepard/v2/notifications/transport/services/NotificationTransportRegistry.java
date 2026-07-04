package de.dlr.shepard.v2.notifications.transport.services;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;
import de.dlr.shepard.v2.notifications.transport.spi.NotificationSender;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — fail-soft registry from
 * {@link TransportKind} to {@link NotificationSender}.
 *
 * <p>Resolves a {@link NotificationTransport} entity to its sender by
 * looking up the entity's {@code kind} string, parsing it to the
 * {@link TransportKind} enum, and returning the registered
 * {@link NotificationSender} for that kind.
 *
 * <p>Per CLAUDE.md "Always: registries are fail-soft":
 * <ul>
 *   <li>{@link #resolve(NotificationTransport)} returns {@link Optional}.</li>
 *   <li>An unrecognised {@code kind} string logs a WARN and returns
 *       {@link Optional#empty()} — the caller skips the send rather than
 *       throwing.</li>
 *   <li>A recognised kind with no registered sender (e.g. SMTP before
 *       NTF1-BACKEND-SMTP lands) also returns {@link Optional#empty()}
 *       — allows the model + LIST + CRUD endpoints to ship before the
 *       senders are wired.</li>
 * </ul>
 *
 * <p>Registration uses CDI {@link Instance}{@code <NotificationSender>}
 * discovery — any {@code @ApplicationScoped} bean implementing the SPI
 * is auto-bound on registry init. The exhaustiveness check at
 * {@link TransportKind} is enforced by javac warnings on the
 * {@code switch} in {@link #knownKindCount()}.
 */
@ApplicationScoped
public class NotificationTransportRegistry {

  private final Map<TransportKind, NotificationSender> byKind =
      new EnumMap<>(TransportKind.class);

  @Inject
  Instance<NotificationSender> discovered;

  /** Populate {@link #byKind} from CDI discovery on first call. */
  private synchronized void ensureBound() {
    if (!byKind.isEmpty()) return;
    if (discovered == null) return;
    for (NotificationSender s : discovered) {
      NotificationSender prev = byKind.put(s.kind(), s);
      if (prev != null) {
        Log.warnf(
          "NTF1: multiple NotificationSender beans for kind=%s; last-loaded wins (was %s, now %s)",
          s.kind(), prev.getClass().getName(), s.getClass().getName());
      }
    }
    Log.infof("NTF1: NotificationTransportRegistry bound %d sender(s) of %d known kind(s)",
        byKind.size(), knownKindCount());
  }

  /**
   * Resolve {@code transport} to its sender, or {@link Optional#empty()}
   * when the kind is unrecognised or no sender is registered. Never throws.
   */
  public Optional<NotificationSender> resolve(NotificationTransport transport) {
    if (transport == null || transport.getKind() == null) {
      return Optional.empty();
    }
    TransportKind kind;
    try {
      kind = TransportKind.valueOf(transport.getKind());
    } catch (IllegalArgumentException e) {
      Log.warnf("NTF1: unknown transport kind '%s' on appId=%s — no sender",
          transport.getKind(), transport.getAppId());
      return Optional.empty();
    }
    ensureBound();
    return Optional.ofNullable(byKind.get(kind));
  }

  /**
   * Programmatic registration used by tests that don't run under
   * CDI discovery. The supplied sender replaces any pre-existing
   * registration for its {@link NotificationSender#kind()}.
   */
  public synchronized void register(NotificationSender sender) {
    byKind.put(sender.kind(), sender);
  }

  /** Test convenience — reset the bound table. */
  public synchronized void clear() {
    byKind.clear();
  }

  /**
   * Count of distinct {@link TransportKind} values. Exhaustive switch
   * over the enum ensures javac warns if a new kind is added without
   * the registry being audited.
   */
  static int knownKindCount() {
    int count = 0;
    for (TransportKind k : TransportKind.values()) {
      // Switch over the enum so adding a kind without a sender impl
      // triggers a -Wall lint at this site.
      count += switch (k) {
        case INAPP, SMTP, MATRIX -> 1;
      };
    }
    return count;
  }
}
