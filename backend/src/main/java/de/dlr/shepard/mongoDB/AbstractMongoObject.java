package de.dlr.shepard.mongoDB;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.util.HasId;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@Data
@NoArgsConstructor
public abstract class AbstractMongoObject implements HasId {

  @Id
  @GeneratedValue
  @JsonIgnore
  private Long id;

  @Index
  @BsonIgnore
  @Schema(readOnly = true)
  private String oid;

  @Schema(readOnly = true, nullable = true, format = "date-time", example = "2024-08-15T11:18:44.632+00:00")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @DateLong
  private Date createdAt;

  /**
   * Constructor
   *
   * @param oid Object Identifier
   */
  protected AbstractMongoObject(String oid) {
    this.oid = oid;
  }

  protected AbstractMongoObject(String oid, Date createdAt) {
    this.oid = oid;
    this.createdAt = createdAt;
  }

  @BsonIgnore
  @Override
  public String getUniqueId() {
    return oid;
  }
}
