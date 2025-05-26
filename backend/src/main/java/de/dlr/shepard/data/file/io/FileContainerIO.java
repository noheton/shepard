package de.dlr.shepard.data.file.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "FileContainer")
public class FileContainerIO extends BasicContainerIO {

  @Schema(readOnly = true, required = true)
  private String oid;

  /**
   * List of collection Ids this container is assigned to as their default container.
   * This field is not returned in the response, if it is set to null.
   */
  @Schema(readOnly = true, nullable = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<Long> defaultCollectionIdList = new ArrayList<>();

  public FileContainerIO(FileContainer container) {
    super(container);
    this.oid = container.getMongoId();

    if (container.getCollectionList() != null) {
      this.defaultCollectionIdList = container
        .getCollectionList()
        .stream()
        .map(Collection::getId)
        .collect(Collectors.toList());
    } else {
      this.defaultCollectionIdList = null;
    }
  }
}
