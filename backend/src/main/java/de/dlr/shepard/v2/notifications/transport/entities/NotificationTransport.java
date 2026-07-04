package de.dlr.shepard.v2.notifications.transport.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * NTF1-BACKEND-TRANSPORT-MODEL — a configured notification transport.
 *
 * <p>Unlike the singleton admin-config nodes ({@code :JupyterConfig},
 * {@code :UnhideConfig}), this is a regular list-shaped entity: an
 * instance can have N transports (e.g. one SMTP relay + two Matrix
 * homeservers + the always-on in-app default), each addressable by
 * {@link #appId}. Per CLAUDE.md "admin-configurable at runtime", every
 * field except the appId/id pair is mutable via
 * {@code PATCH /v2/admin/notifications/transports/{appId}}.
 *
 * <p><b>Write-only fields.</b> {@link #smtpPassword} and
 * {@link #matrixAccessToken} are stored on the entity but never appear
 * in {@code NotificationTransportReadIO} — the read path returns a
 * different IO that omits the field entirely (not nulls it). A regression
 * test ({@code NotificationTransportReadIOTest}) protects this invariant.
 *
 * <p><b>Kind-specific fields.</b> All kind-specific fields are nullable
 * strings; the entity is a flat bag rather than a discriminated subtype
 * because OGM's polymorphism support is fragile and the kind set is small
 * + closed (per {@link TransportKind}). The {@code NotificationSender}
 * SPI is responsible for reading only the fields its kind needs.
 *
 * <p><b>Migration.</b> {@code V96__Ntf1_NotificationTransport_scaffold.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on {@code :NotificationTransport}.
 * Pre-NTF1 installs see zero rows on first start; transports are created
 * explicitly via the admin REST.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class NotificationTransport implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /** UUID v7. Minted on save by {@code GenericDAO.createOrUpdate} when null. */
  @Property("appId")
  private String appId;

  /**
   * Discriminator. Stored as the literal enum name (string) so
   * {@code cypher-shell} reads cleanly. Required (no transport is
   * useful without a kind); the REST layer rejects creates that omit it.
   */
  @Property("kind")
  private String kind;

  /** Operator-friendly label rendered in the admin pane. Required. */
  @Property("name")
  private String name;

  /**
   * Whether this transport should be invoked when its kind is selected
   * for delivery. Disabled transports are kept in the list (so
   * credentials don't have to be re-entered) but the registry skips them.
   */
  @Property("enabled")
  private boolean enabled;

  /** Outcome of the most-recent per-transport test send: {@code OK} / {@code FAIL} / null. */
  @Property("lastTestResult")
  private String lastTestResult;

  /** Epoch millis of the most-recent per-transport test send; null if never tested. */
  @Property("lastTestedAt")
  private Long lastTestedAt;

  /** Optional human-readable detail for the last test attempt (error message or "delivered"). */
  @Property("lastTestDetail")
  private String lastTestDetail;

  // ─── SMTP-specific (all nullable) ───────────────────────────────────────────

  @Property("smtpHost")
  private String smtpHost;

  /** SMTP port. Stored as {@code Integer} so "unset" is distinguishable. */
  @Property("smtpPort")
  private Integer smtpPort;

  @Property("smtpUsername")
  private String smtpUsername;

  /**
   * SMTP password. WRITE-ONLY on the REST surface: present on POST/PATCH
   * bodies, omitted entirely from GET responses. Stored as plain text in
   * Neo4j — operators MUST run the database on an encrypted volume.
   * (Out of scope for NTF1-BACKEND-*: secrets vault integration tracked
   * as a separate item in the design doc §7.)
   */
  @Property("smtpPassword")
  private String smtpPassword;

  /** RFC 5321 reverse-path / RFC 5322 From header. Required when kind=SMTP. */
  @Property("smtpFrom")
  private String smtpFrom;

  /**
   * Whether to STARTTLS on the SMTP session.
   * Tri-state: null = use jakarta.mail default (no STARTTLS); true/false explicit.
   */
  @Property("smtpTls")
  private Boolean smtpTls;

  // ─── Matrix-specific (all nullable) ─────────────────────────────────────────

  /** Matrix homeserver base URL (e.g. {@code https://matrix.example.org}). */
  @Property("matrixHomeserver")
  private String matrixHomeserver;

  /**
   * Matrix access token. WRITE-ONLY on the REST surface — same shape as
   * {@link #smtpPassword}.
   */
  @Property("matrixAccessToken")
  private String matrixAccessToken;

  /**
   * Default Matrix room ID (e.g. {@code !abcdef:matrix.example.org}).
   * When a notification doesn't carry a per-message room override, this
   * is where it goes.
   */
  @Property("matrixDefaultRoom")
  private String matrixDefaultRoom;

  /** For testing purposes only. */
  public NotificationTransport(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof NotificationTransport other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
