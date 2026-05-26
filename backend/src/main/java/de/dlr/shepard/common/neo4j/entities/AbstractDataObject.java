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
   * FAIR2 — ORCID of the researcher who created this DataObject, stamped
   * server-side at creation time from {@code User.orcid}. Nullable; null when
   * the creating user has not set an ORCID at the time of creation, or for
   * entities created before FAIR2 shipped. Survives user-account deletion so
   * provenance is durable. Read-only for API callers (never accepted as input).
   */
  @org.neo4j.ogm.annotation.Property("createdByOrcid")
  private String createdByOrcid;

  /**
   * FAIR3 — ISO-8601 date string (e.g. {@code "2027-12-31"}) after which the
   * embargo lifts. Only meaningful when {@code accessRights=EMBARGOED}. Nullable;
   * null means no specific end-date has been declared. The
   * {@code PublishService.publish(...)} call rejects RESTRICTED/EMBARGOED entities
   * unless {@code force=true}; this field is informational for the rejection message.
   */
  @org.neo4j.ogm.annotation.Property("embargoEndDate")
  private String embargoEndDate;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected AbstractDataObject(long id) {
    super(id);
  }
}
