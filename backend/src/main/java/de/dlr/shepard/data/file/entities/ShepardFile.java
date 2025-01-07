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
