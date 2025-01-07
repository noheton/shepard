package de.dlr.shepard.common.neo4j.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.common.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "HasId")
public class HasIdIO implements HasId {

  @JsonProperty
  private String uniqueId;

  public HasIdIO(HasId obj) {
    this.uniqueId = obj.getUniqueId();
  }
}
