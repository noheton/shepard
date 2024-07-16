package de.dlr.shepard.mongoDB;

import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.Document;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class StructuredData extends AbstractMongoObject {

  @Schema(nullable = true)
  private String name;

  public StructuredData(String name, Date createdAt) {
    setCreatedAt(createdAt);
    this.name = name;
  }

  public StructuredData(String oid, Date createdAt, String name) {
    super(oid, createdAt);
    this.name = name;
  }

  /**
   * Converts a document to StructuredData
   *
   * @param doc Document
   */
  public StructuredData(Document doc) {
    super(doc.getString("oid"), doc.getDate("createdAt"));
    this.name = doc.getString("name");
  }
}
