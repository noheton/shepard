package de.dlr.shepard.data.file.entities;

import de.dlr.shepard.common.mongoDB.AbstractMongoObject;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.NodeEntity;

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
