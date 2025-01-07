package de.dlr.shepard.context.references.structureddata.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "StructuredDataReference")
public class StructuredDataReferenceIO extends BasicReferenceIO {

  @NotEmpty
  @Schema(required = true)
  private String[] structuredDataOids;

  @NotNull
  @Schema(required = true)
  private long structuredDataContainerId;

  public StructuredDataReferenceIO(StructuredDataReference ref) {
    super(ref);
    this.structuredDataOids = ref.getStructuredDatas().stream().map(StructuredData::getOid).toArray(String[]::new);
    this.structuredDataContainerId = ref.getStructuredDataContainer() != null
      ? ref.getStructuredDataContainer().getId()
      : -1;
  }
}
