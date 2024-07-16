package de.dlr.shepard.mongoDB;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.util.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonIgnore;
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
  @Schema(accessMode = AccessMode.READ_ONLY)
  private String oid;

  @Schema(accessMode = AccessMode.READ_ONLY, nullable = true)
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
