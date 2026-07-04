package de.dlr.shepard.v2.notifications.transport.entities;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — the catalogue of notification transport
 * kinds the registry knows how to dispatch through.
 *
 * <p>Stored as the literal enum name on the {@link NotificationTransport}
 * Neo4j entity ({@code kind} property). String round-tripping keeps the
 * wire/storage format human-readable in {@code cypher-shell}.
 *
 * <p>Each enum value is the dispatch key for a
 * {@code NotificationSender} SPI implementation resolved by
 * {@code NotificationTransportRegistry}. Adding a new transport kind here
 * triggers a javac warning at the registry's {@code switch} until the
 * matching {@code NotificationSender} is wired up — the exhaustiveness
 * check is the load-bearing reason this is an enum and not a free-form
 * string.
 */
public enum TransportKind {
  /** In-app notification (already shipped — {@code NotificationService.publish}). */
  INAPP,
  /** SMTP email transport (NTF1-BACKEND-SMTP). */
  SMTP,
  /** Matrix room message transport (NTF1-BACKEND-MATRIX). */
  MATRIX
}
