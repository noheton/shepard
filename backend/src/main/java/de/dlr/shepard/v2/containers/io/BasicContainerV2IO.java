package de.dlr.shepard.v2.containers.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2-only base for container IO classes on the {@code /v2/} surface.
 *
 * <p>Suppresses the legacy {@code id} (Neo4j internal node-id) from the
 * wire shape. {@link BasicContainerIO} is shared with the frozen
 * {@code /shepard/api/} v1 surface and cannot be modified; this class
 * provides the v2-specific suppression as a side-car subclass following
 * the same pattern as {@code BasicReferenceV2IO} for references.
 *
 * <p>The v1 surface is unaffected — {@link BasicContainerIO} is not touched.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonIgnoreProperties({"id"})
@Schema(name = "BasicContainerV2")
public abstract class BasicContainerV2IO extends BasicContainerIO {

  public BasicContainerV2IO(BasicContainer container) {
    super(container);
  }

  /** Route the HasId contract through appId (the stable v2 address). */
  @Override
  public String getUniqueId() {
    return getAppId();
  }
}
