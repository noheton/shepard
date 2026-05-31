package de.dlr.shepard.v2.notifications.transport.spi;

import de.dlr.shepard.v2.notifications.transport.entities.NotificationTransport;
import de.dlr.shepard.v2.notifications.transport.entities.TransportKind;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — SPI for delivering a notification via a
 * configured {@link NotificationTransport}.
 *
 * <p>Implementations are {@code @ApplicationScoped} CDI beans registered
 * by {@link de.dlr.shepard.v2.notifications.transport.services.NotificationTransportRegistry}
 * which resolves a transport's {@link TransportKind} to the matching
 * sender. Adding a new kind to {@link TransportKind} triggers an
 * exhaustiveness warning at the registry until a sender for the new kind
 * is wired up — the load-bearing reason {@code kind} is an enum.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li><b>Pure of side effects beyond the delivery itself.</b> No
 *       provenance writes (those happen at the REST/service layer); no
 *       state mutation on the {@code NotificationTransport} entity
 *       (the registry handles {@code lastTestResult} / {@code lastTestedAt}
 *       updates).</li>
 *   <li><b>Synchronous + bounded.</b> No background threads, no
 *       reactive plumbing; return {@code true} on success, {@code false}
 *       on a recoverable failure, throw on programming errors only.</li>
 *   <li><b>Thread-safe.</b> CDI {@code @ApplicationScoped} beans are
 *       singletons; per-send state lives on the stack or in
 *       per-send-scoped objects.</li>
 * </ul>
 */
public interface NotificationSender {

  /** Which {@link TransportKind} this sender handles. */
  TransportKind kind();

  /**
   * Deliver {@code message} via {@code transport}.
   *
   * @return {@code true} if the transport accepted the message,
   *         {@code false} on a recoverable failure (network error,
   *         remote 5xx, etc.) — the caller logs + updates
   *         {@code lastTestResult}. Programming errors (null arguments,
   *         malformed transport entity) propagate as {@link RuntimeException}.
   */
  boolean send(NotificationTransport transport, NotificationMessage message);
}
