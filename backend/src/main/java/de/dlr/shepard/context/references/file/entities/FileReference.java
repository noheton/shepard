package de.dlr.shepard.context.references.file.entities;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
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
public class FileReference extends BasicReference {

  @Relationship(type = Neo4jLabels.HAS_PAYLOAD)
  private List<ShepardFile> files = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Neo4jLabels.IS_IN_CONTAINER)
  private FileContainer fileContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public FileReference(long id) {
    super(id);
  }

  public void addFile(ShepardFile file) {
    files.add(file);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(files);
    result = prime * result + HasId.hashcodeHelper(fileContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof FileReference)) return false;
    FileReference other = (FileReference) obj;
    return HasId.equalsHelper(fileContainer, other.fileContainer) && Objects.equals(files, other.files);
  }
}
