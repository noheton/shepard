package de.dlr.shepard.common.neo4j.io;

import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.neo4j.io.validation.NoDelimiterInMapKeys;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "AbstractDataObject")
public abstract class AbstractDataObjectIO extends BasicEntityIO {

  @Schema(nullable = true)
  private String description;

  @NoDelimiterInMapKeys
  private Map<String, String> attributes = new HashMap<>();

  protected AbstractDataObjectIO(AbstractDataObject dataObject) {
    super(dataObject);
    this.description = dataObject.getDescription();
    this.attributes = dataObject.getAttributes();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof AbstractDataObjectIO)) return false;
    AbstractDataObjectIO other = (AbstractDataObjectIO) o;
    return (Objects.equals(description, other.description) && Objects.equals(attributes, other.attributes));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(description, attributes);
    return result;
  }
}
