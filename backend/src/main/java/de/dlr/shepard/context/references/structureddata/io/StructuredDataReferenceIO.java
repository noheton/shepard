package de.dlr.shepard.context.references.structureddata.io;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    StructuredDataReferenceIO other = (StructuredDataReferenceIO) o;
    return (
      structuredDataContainerId == other.structuredDataContainerId &&
      HasId.areEqualSets(structuredDataOids, other.structuredDataOids)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((Long) structuredDataContainerId).hashCode();
    result = prime * result + HasId.hashcodeHelper(structuredDataOids);
    return result;
  }
}
