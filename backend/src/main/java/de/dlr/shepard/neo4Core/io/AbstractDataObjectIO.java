package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.AbstractDataObject;
import de.dlr.shepard.neo4Core.io.validation.NoDelimiterInMapKeys;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
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
}
