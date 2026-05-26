package de.dlr.shepard.data.file.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.common.mongoDB.AbstractMongoObject;
import java.time.Instant;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

@NodeEntity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class ShepardFile extends AbstractMongoObject {

  @Schema(readOnly = true)
  private String filename;

  @Schema(readOnly = true, nullable = true)
  private String md5;

  /**
   * Size of the underlying GridFS payload in bytes. Captured at upload
   * time per FB1a (aidocs/16); existing rows uploaded before FB1a stay
   * {@code null} until they are re-uploaded.
   */
  @Schema(readOnly = true, nullable = true, description = "Payload size in bytes. Null for files uploaded before FB1a.")
  private Long fileSize;

  /**
   * FS1a — id of the {@link de.dlr.shepard.storage.FileStorage}
   * adapter that holds the bytes for this file. Defaults to
   * {@code "gridfs"} for new uploads (the in-core GridFS adapter);
   * {@code V34__Backfill_FilePayload_providerId.cypher} stamps the
   * same value onto pre-FS1a rows so the {@link
   * de.dlr.shepard.storage.FileStorageRegistry} can route reads
   * unambiguously.
   *
   * <p>Internal bookkeeping — not surfaced in the public wire shape
   * to keep the upstream {@code /shepard/api/...} contract
   * byte-identical. {@code @JsonIgnore} hides it from REST IO,
   * {@code @BsonIgnore} keeps it out of the Mongo bookkeeping
   * documents (the same posture as {@code AbstractMongoObject.appId}).
   * Future FS1c admin reads can opt-in via a dedicated IO type.
   */
  @Property("providerId")
  @BsonIgnore
  @JsonIgnore
  @Schema(hidden = true)
  private String providerId;

  /**
   * FS1e3 — id of the {@link de.dlr.shepard.storage.FileStorage}
   * adapter that previously held this file's bytes, before the most
   * recent {@code FileMigrationService} swap stamped {@link #providerId}
   * with the new active adapter. {@code null} on rows that have never
   * been migrated (which is most of them — pre-FS1e3 rows stay
   * {@code null} until a future migration touches them).
   *
   * <p>Set together with {@link #previousLocator}, {@link #migratedAt},
   * and {@link #migrationHmac} in a single Cypher SET so the
   * "stamp before swap" invariant holds inside one transaction. The
   * operator-facing {@code POST /v2/admin/files/migrate/rollback/{appId}}
   * endpoint reads these fields to recover from a mid-migration
   * failure on a per-file basis.
   *
   * <p>Internal bookkeeping — not surfaced in any public wire shape.
   * Same {@code @JsonIgnore} + {@code @BsonIgnore} + {@code @Schema(hidden=true)}
   * posture as {@link #providerId} so the v5
   * {@code /shepard/api/...fileContainers/.../files/{id}} GET stays
   * byte-identical to upstream.
   */
  @Property("previousProviderId")
  @BsonIgnore
  @JsonIgnore
  @Schema(hidden = true)
  private String previousProviderId;

  /**
   * FS1e3 — the storage-adapter-specific locator string that
   * referenced this file's bytes under the {@link #previousProviderId}
   * adapter before the migration swap. Preserves the source format
   * (e.g. {@code "<containerMongoId>:<oid>"} for GridFS,
   * {@code "<containerMongoId>/<oid>"} for S3) so a per-file rollback
   * can re-write bytes to the previous adapter at the exact same
   * locator the original FS1e1 OID-preservation contract guaranteed.
   *
   * <p>{@code null} on never-migrated rows. Internal bookkeeping —
   * same wire-hidden posture as {@link #providerId}.
   */
  @Property("previousLocator")
  @BsonIgnore
  @JsonIgnore
  @Schema(hidden = true)
  private String previousLocator;

  /**
   * FS1e3 — instant of the most recent migration swap. {@code null}
   * on never-migrated rows.
   *
   * <p>Operator forensic / audit aid: cross-references with the
   * {@code FileMigrationService} structured log line
   * {@code "moved oid=... (source → target)"} to pin a per-file
   * migration time. Future FS1e6 audit-node design may aggregate
   * across rows, but the per-file timestamp here is the smallest
   * useful unit for "when did this single file flip?".
   *
   * <p>Internal bookkeeping — same wire-hidden posture as
   * {@link #providerId}.
   */
  @Property("migratedAt")
  @BsonIgnore
  @JsonIgnore
  @Schema(hidden = true)
  private Instant migratedAt;

  /**
   * FS1e3 — optional integrity hash captured at migration time.
   * {@code null} today (the migration loop intentionally does not
   * compute a SHA-256 over the streamed bytes — that's out of scope
   * for this PR, per the FS1e3 task spec's "no behaviour split yet —
   * just record-keeping" rule). Future-proof for the FS1e6 audit-node
   * + verify endpoint, which will populate this field from the
   * destination provider's verification step.
   *
   * <p>The field name {@code migrationHmac} is intentionally generic —
   * the integrity check may be SHA-256, an HMAC over (oid, sizeBytes,
   * sha256), or whatever the FS1e6 design lands on; readers should
   * treat the value as opaque + comparison-only.
   *
   * <p>Internal bookkeeping — same wire-hidden posture as
   * {@link #providerId}.
   */
  @Property("migrationHmac")
  @BsonIgnore
  @JsonIgnore
  @Schema(hidden = true)
  private String migrationHmac;

  public ShepardFile(Date createdAt, String filename, String md5) {
    setCreatedAt(createdAt);
    this.filename = filename;
    this.md5 = md5;
  }

  public ShepardFile(String oid, Date createdAt, String filename, String md5) {
    super(oid, createdAt);
    this.filename = filename;
    this.md5 = md5;
  }
}
