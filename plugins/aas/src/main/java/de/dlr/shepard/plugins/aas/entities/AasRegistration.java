package de.dlr.shepard.plugins.aas.entities;

import de.dlr.shepard.common.identifier.HasAppId;
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
 * AAS1-reg outbox entity. One row per (shell, registry-url) pair, tracking the
 * registration state of a single `:AssetAdministrationShell` at a specific
 * external IDTA AAS Registry.
 *
 * <p>Registration is <em>observability + discoverability</em>, not correctness:
 * the shepard write-path never blocks on registry calls. When a registration
 * attempt fails, this row flips to {@link Status#FAILED} with the error message
 * captured in {@link #errorMessage}; the operator recovers via
 * {@code POST /v2/admin/aas/registrations/sync} (AAS1-reg Commit 3).
 *
 * <p>The outbox is populated by {@code AasRegistryOutboxService} (AAS1-reg
 * Commit 2) on every shell create / update / delete event. The admin listing
 * endpoint {@code GET /v2/admin/aas/registrations} surfaces this table.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AasRegistration implements HasAppId {

  /** IDTA AAS Registry registration status. */
  public enum Status {
    /** Registration enqueued but not yet attempted or in-flight. */
    PENDING,
    /** Registration confirmed by the registry (2xx response). */
    SYNCED,
    /** Last registration attempt failed; {@link #errorMessage} carries details. */
    FAILED,
  }

  @Id
  @GeneratedValue
  private Long id;

  /** Application-level identifier (UUID v7). Minted on save by {@code GenericDAO.createOrUpdate}. */
  @Property("appId")
  private String appId;

  /** Base URL of the external IDTA AAS Registry this row targets. */
  @Property("registryUrl")
  private String registryUrl;

  /**
   * {@code appId} of the {@code :Collection} (= AAS Shell) being registered.
   * Matches the {@code shellId} in the IDTA Shell descriptor.
   */
  @Property("shellAppId")
  private String shellAppId;

  /** Current outbox state. Set to {@link Status#PENDING} on insert. */
  @Property("status")
  private Status status = Status.PENDING;

  /** Epoch millis of the most recent registration attempt; {@code null} until first try. */
  @Property("lastAttemptAt")
  private Long lastAttemptAt;

  /** HTTP status or exception message from the last failed attempt; {@code null} on success. */
  @Property("errorMessage")
  private String errorMessage;

  /** Epoch millis when this outbox row was created. */
  @Property("createdAt")
  private Long createdAt;

  /** Epoch millis when this outbox row was last modified. */
  @Property("updatedAt")
  private Long updatedAt;

  /** For testing purposes only. */
  public AasRegistration(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AasRegistration other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
