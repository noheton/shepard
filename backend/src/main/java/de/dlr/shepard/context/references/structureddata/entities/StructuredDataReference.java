package de.dlr.shepard.context.references.structureddata.entities;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class StructuredDataReference extends BasicReference {

  @Relationship(type = Neo4jLabels.HAS_PAYLOAD)
  private List<StructuredData> structuredDatas = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Neo4jLabels.IS_IN_CONTAINER)
  private StructuredDataContainer structuredDataContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public StructuredDataReference(long id) {
    super(id);
  }

  public void addStructuredData(StructuredData structuredData) {
    structuredDatas.add(structuredData);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(structuredDatas);
    result = prime * result + HasId.hashcodeHelper(structuredDataContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof StructuredDataReference)) return false;
    StructuredDataReference other = (StructuredDataReference) obj;
    return (
      HasId.equalsHelper(structuredDataContainer, other.structuredDataContainer) &&
      Objects.equals(structuredDatas, other.structuredDatas)
    );
  }
}
