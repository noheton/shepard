package de.dlr.shepard.data.file.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.common.mongoDB.AbstractMongoObject;
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
