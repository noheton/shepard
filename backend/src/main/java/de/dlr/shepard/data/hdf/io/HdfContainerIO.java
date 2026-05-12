package de.dlr.shepard.data.hdf.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A5a Phase 1 — wire shape for {@code /v2/hdf-containers/...}.
 *
 * <p>Carries the public-facing {@code appId} (L2d-style identifier),
 * the operator-visible {@link #hsdsDomain}, the optional
 * {@link #description}, and the free-form
 * {@link #attributes} map. The legacy long-id {@code id} +
 * {@code shepardId} envelope from {@link BasicContainerIO} stay on
 * the wire so the existing search / breadcrumb code keeps working
 * without retrofitting until L2e drops them.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "HdfContainer")
public class HdfContainerIO extends BasicContainerIO {

  /**
   * Application-level identifier (UUID v7). Read-only on the wire;
   * set by the server on create. Operators / clients citing this
   * container in URLs use {@code appId}, not the legacy long-id.
   */
  @Schema(readOnly = true)
  private String appId;

  /**
   * HSDS domain path the container is provisioned at, e.g.
   * {@code /shepard/<container-appId>/}. Read-only on the wire — the
   * server picks it on create. Surfaced so admins can correlate
   * shepard containers with HSDS-side rows in {@code hsadmin}.
   */
  @Schema(readOnly = true)
  private String hsdsDomain;

  /**
   * Free-form description, optional on the wire.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String description;

  /**
   * Free-form key/value metadata. Same delimiter idiom as the rest
   * of the codebase (post-V9). Empty / null tolerated.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> attributes = new HashMap<>();

  public HdfContainerIO(HdfContainer container) {
    super(container);
    this.appId = container.getAppId();
    this.hsdsDomain = container.getHsdsDomain();
    this.description = container.getDescription();
    this.attributes = container.getAttributes() == null ? new HashMap<>() : new HashMap<>(container.getAttributes());
  }

  /**
   * Helper to map a list of entities to their IOs in one pass.
   */
  public static List<HdfContainerIO> fromEntities(List<HdfContainer> entities) {
    if (entities == null) return List.of();
    return entities.stream().map(HdfContainerIO::new).collect(Collectors.toList());
  }
}
