package de.dlr.shepard.data.file.entities;

import de.dlr.shepard.common.neo4j.entities.AbstractEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * PV1a — a point-in-time record of one byte payload upload for a file stored
 * inside a {@link FileContainer}.
 *
 * <p>Every time a file is uploaded (created or re-uploaded) through
 * {@code FileContainerService.createFile}, a {@code PayloadVersion} node is
 * minted and stored in Neo4j. The node captures the content hash, byte count,
 * uploader identity, and a monotonically-increasing version number scoped to
 * the {@code (containerAppId, originalName)} pair.
 *
 * <p>Nodes are append-only: they are never mutated after creation. The list
 * of versions for a given file is therefore a full audit trail of every upload.
 *
 * <p>Fields inherited from {@link AbstractEntity}:
 * <ul>
 *   <li>{@code appId} — UUID v7, minted on save by {@code GenericDAO}.</li>
 *   <li>{@code createdAt}, {@code createdBy}, {@code deleted} — standard.</li>
 * </ul>
 *
 * <p>Cross-references: {@code aidocs/data/46} PV1a scope;
 * {@code aidocs/16} PV1a backlog row.
 */
@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class PayloadVersion extends AbstractEntity {

  /**
   * The GridFS {@code ObjectId} hex string identifying the uploaded bytes.
   * Null for files uploaded via presigned URL path (no GridFS).
   */
  @Property
  private String fileOid;

  /**
   * SHA-256 hex digest of the uploaded payload bytes.
   * Upper-case hex, 64 characters.
   */
  @Property
  private String sha256;

  /**
   * Byte count of the uploaded payload as reported by GridFS.
   * Null when the storage backend could not determine the size.
   */
  @Property
  private Long sizeBytes;

  /**
   * Monotonically-increasing version number scoped to the
   * {@code (containerAppId, originalName)} pair.
   * Version 1 is the first upload; each subsequent upload increments
   * this counter by 1.
   */
  @Property
  private long versionNumber;

  /**
   * Username of the caller who triggered this upload, frozen at creation time
   * as a scalar property. Separate from the {@code :CREATED_BY} relationship
   * so the identity is readable even if the User entity changes.
   */
  @Property
  private String uploadedBy;

  /**
   * ISO-8601 UTC timestamp at which the upload was received, frozen at
   * creation time. Stored as a String to avoid converter dependencies.
   * Example: {@code "2026-05-17T12:34:56Z"}.
   */
  @Property
  private String uploadedAt;

  /**
   * The {@code appId} of the {@link FileContainer} this version belongs to.
   * Stored as a scalar property rather than a relationship so the version
   * list can be read with a single Cypher query without loading the container.
   */
  @Property
  private String containerAppId;

  /**
   * The original file name as supplied by the uploader (or the date-stamped
   * default assigned by {@code FileContainerService.createFile}).
   */
  @Property
  private String originalName;
}
