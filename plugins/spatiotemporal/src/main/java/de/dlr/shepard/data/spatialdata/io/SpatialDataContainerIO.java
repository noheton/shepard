package de.dlr.shepard.data.spatialdata.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.List;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Schema(name = "SpatialDataContainer")
public class SpatialDataContainerIO extends BasicContainerIO {

  /**
   * MFFD-SPATIAL-FRAME-HANDSHAKE — optional FK-by-convention pointing at a
   * {@code :CoordinateFrame.appId} (CST1, {@code aidocs/data/85}). Mirrors the
   * PostGIS {@code coord_frame_app_id} column. Omitted from the wire when null
   * so the legacy {@code /shepard/api/spatialDataContainers} surface stays
   * byte-identical to its pre-change shape (no upstream breakage).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(nullable = true, description = "Optional CoordinateFrame appId (CST1) this container is anchored in.")
  private String frameAppId;

  public SpatialDataContainerIO(SpatialDataContainer container) {
    super(container);
    this.frameAppId = container.getFrameAppId();
  }

  public static SpatialDataContainerIO fromEntity(SpatialDataContainer entity) {
    return new SpatialDataContainerIO(entity);
  }

  public static List<SpatialDataContainerIO> fromEntities(List<SpatialDataContainer> entities) {
    return entities.stream().map(SpatialDataContainerIO::fromEntity).collect(Collectors.toList());
  }
}
