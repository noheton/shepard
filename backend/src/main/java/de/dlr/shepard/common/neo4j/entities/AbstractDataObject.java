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
   * FAIR-1 (LIC1) — SPDX license identifier expression (e.g. {@code "CC-BY-4.0"},
   * {@code "MIT"}, {@code "Apache-2.0"}, {@code "ODbL-1.0"}) or {@code "PROPRIETARY"}.
   * Nullable; null means "not yet declared". See
   * {@code V57__NOOP_AbstractDataObject_fair_fields.cypher} — additive schema-free
   * property.
   */
  private String license;

  /**
   * FAIR-1 (LIC1) — Access-rights enum stored as a String: one of {@code OPEN},
   * {@code RESTRICTED}, {@code CLOSED}, or {@code EMBARGOED}. Nullable; null means
   * "not yet declared". Enforcement is currently client-side via the v-select; the
   * server stays permissive for additive forward compatibility.
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
