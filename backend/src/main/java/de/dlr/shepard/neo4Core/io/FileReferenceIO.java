package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.entities.FileReference;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "FileReference")
public class FileReferenceIO extends BasicReferenceIO {

  @NotEmpty
  private String[] fileOids;

  @NotNull
  private long fileContainerId;

  public FileReferenceIO(FileReference ref) {
    super(ref);
    this.fileOids = ref.getFiles().stream().map(ShepardFile::getOid).toArray(String[]::new);
    this.fileContainerId = ref.getFileContainer() != null ? ref.getFileContainer().getId() : -1;
  }
}
