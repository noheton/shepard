package de.dlr.shepard.context.references.file.io;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.data.file.entities.ShepardFile;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "FileReference")
public class FileReferenceIO extends BasicReferenceIO {

  @NotEmpty
  @Schema(required = true)
  private String[] fileOids;

  @NotNull
  @Schema(required = true)
  private long fileContainerId;

  public FileReferenceIO(FileReference ref) {
    super(ref);
    this.fileOids = ref.getFiles().stream().map(ShepardFile::getOid).toArray(String[]::new);
    this.fileContainerId = ref.getFileContainer() != null ? ref.getFileContainer().getId() : -1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    FileReferenceIO other = (FileReferenceIO) o;
    return (fileContainerId == other.fileContainerId && HasId.areEqualSets(fileOids, other.fileOids));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((Long) fileContainerId).hashCode();
    result = prime * result + HasId.hashcodeHelper(fileOids);
    return result;
  }
}
