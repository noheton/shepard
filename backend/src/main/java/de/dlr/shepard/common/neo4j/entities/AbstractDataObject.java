package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.context.version.entities.VersionableEntity;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Properties;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractDataObject extends VersionableEntity {

  private String description;

  private String status;

  @ToString.Exclude
  @Properties(delimiter = "||")
  private Map<String, String> attributes;

  /**
   * FAIR-1 — SPDX expression or other license identifier (e.g. "CC-BY-4.0").
   * Nullable; null means "not yet declared".
   */
  private String license;

  /**
   * FAIR-1 — COAR Access Rights vocabulary term (e.g. "open access",
   * "embargoed access", "restricted access", "metadata only access").
   * Nullable; null means "not yet declared".
   */
  private String accessRights;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected AbstractDataObject(long id) {
    super(id);
  }
}
