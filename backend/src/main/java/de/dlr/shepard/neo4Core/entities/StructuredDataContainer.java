package de.dlr.shepard.neo4Core.entities;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.util.Constants;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StructuredDataContainer extends BasicContainer {

  private String mongoId;

  @Relationship(type = Constants.STRUCTUREDDATA_IN_CONTAINER)
  private List<StructuredData> structuredDatas = new ArrayList<>();

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public StructuredDataContainer(long id) {
    super(id);
  }

  public void addStructuredData(StructuredData structuredData) {
    structuredDatas.add(structuredData);
  }
}
