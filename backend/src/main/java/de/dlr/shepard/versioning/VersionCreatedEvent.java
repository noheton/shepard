package de.dlr.shepard.versioning;

import java.util.Objects;

/**
 * ENT1a CDI event fired by
 * {@link EntityVersionService#createVersion(String, String, String, String, String)}
 * after a fresh {@link EntityVersion} has been committed. Mirrors the
 * shape of A5b's {@code PermissionsChangedEvent} per CLAUDE.md's
 * "best-effort observer" idiom.
 *
 * <p><strong>Best-effort semantics.</strong> Observers never block
 * the user's POST and never throw back into the firing path —
 * {@code EntityVersionService} catches every {@code RuntimeException}
 * from {@code @Observes} listeners and logs a WARN. The shepard write
 * is the source of truth; downstream consumers reconcile out-of-band.
 *
 * <p>Anticipated consumers (none today; queued for ENT1b–e):
 * <ul>
 *   <li>ENT1b: kick the file-payload Copy-on-Write pipeline so the
 *       new version owns a fresh snapshot of every file blob it
 *       references.</li>
 *   <li>ENT1c: reconcile with KIP1h's
 *       {@code Publication.versionNumber} when a publish call
 *       implicitly creates the next version.</li>
 *   <li>Future: federation hooks — mirror the version row into
 *       sibling instances for cross-shepard citation.</li>
 * </ul>
 */
public final class VersionCreatedEvent {

  private final String parentEntityKind;
  private final String parentEntityAppId;
  private final String versionAppId;
  private final String versionLabel;
  private final int versionOrdinal;
  private final String createdBy;

  public VersionCreatedEvent(
    String parentEntityKind,
    String parentEntityAppId,
    String versionAppId,
    String versionLabel,
    int versionOrdinal,
    String createdBy
  ) {
    this.parentEntityKind = Objects.requireNonNull(parentEntityKind, "parentEntityKind");
    this.parentEntityAppId = Objects.requireNonNull(parentEntityAppId, "parentEntityAppId");
    this.versionAppId = Objects.requireNonNull(versionAppId, "versionAppId");
    this.versionLabel = Objects.requireNonNull(versionLabel, "versionLabel");
    this.versionOrdinal = versionOrdinal;
    this.createdBy = createdBy;
  }

  public String getParentEntityKind() {
    return parentEntityKind;
  }

  public String getParentEntityAppId() {
    return parentEntityAppId;
  }

  public String getVersionAppId() {
    return versionAppId;
  }

  public String getVersionLabel() {
    return versionLabel;
  }

  public int getVersionOrdinal() {
    return versionOrdinal;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  @Override
  public String toString() {
    return (
      "VersionCreatedEvent{parentEntityKind='" +
      parentEntityKind +
      "', parentEntityAppId='" +
      parentEntityAppId +
      "', versionAppId='" +
      versionAppId +
      "', versionLabel='" +
      versionLabel +
      "', versionOrdinal=" +
      versionOrdinal +
      ", createdBy='" +
      createdBy +
      "'}"
    );
  }
}
