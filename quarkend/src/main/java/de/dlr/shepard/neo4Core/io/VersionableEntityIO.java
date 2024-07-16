package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.VersionableEntity;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "VersionableEntity")
public class VersionableEntityIO extends BasicEntityIO {

  public VersionableEntityIO(VersionableEntity versionableEntity) {
    super(versionableEntity);
    setId(versionableEntity.getShepardId());
  }

  protected static long[] extractShepardIds(List<? extends VersionableEntity> entities) {
    var result = entities.stream().map(VersionableEntity::getShepardId).mapToLong(Long::longValue).toArray();
    return result;
  }
}
