package de.dlr.shepard.template.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * First-class admin-managed template, per {@code aidocs/54}. Replaces
 * the {@code __templates} double-underscore sentinel hack that
 * {@code aidocs/39} originally proposed (and that this fork hasn't
 * shipped yet — the cheapest moment to redirect is now, before any
 * {@code __templates} data lands).
 *
 * <p>Lives in an admin-only subgraph: only callers carrying the
 * {@code instance-admin} role (per {@code aidocs/51}) can mint /
 * edit / retire templates. Reading is gated at the
 * {@code :ALLOWS_TEMPLATE} edge level — Collection-owners curate the
 * subset of templates visible inside their Collection — but the
 * picker UI calls a future {@code /v2/templates} listing endpoint
 * (T1b).
 *
 * <p>Versioning is copy-on-write (T1a §2): each edit mints a new
 * node with {@code version} incremented and a {@code :SUPERSEDES}
 * edge back to the prior. The prior node is marked
 * {@code retired = true} and filtered from picker listings, but
 * stays in place so existing {@code (:Collection)-[:USES_TEMPLATE]->}
 * citations remain valid (and reproducible per {@code aidocs/41}
 * snapshots).
 */
@NodeEntity
@Data
@NoArgsConstructor
public class ShepardTemplate implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam.
   */
  @Property("appId")
  private String appId;

  /**
   * Human-readable template name — surfaced in the picker UI.
   * Not enforced unique because copy-on-write versioning means a
   * "v2 of the LUMEN run recipe" carries the same name as the v1.
   */
  @Property("name")
  private String name;

  /**
   * One of {@code DATAOBJECT_RECIPE} / {@code COLLECTION_RECIPE} /
   * {@code EXPERIMENT_RECIPE} (per {@code aidocs/50}) — open-ended;
   * future template kinds plug in here. Free-form to allow plugins
   * to register their own.
   */
  @Property("templateKind")
  private String templateKind;

  /**
   * Monotonically increasing version per template-name within a kind.
   * Bumped on each copy-on-write edit. v1 templates are minted with
   * {@code version=1}.
   */
  @Property("version")
  private Integer version;

  /**
   * The JSON DSL body (per {@code aidocs/54 §7}). Recommendation in
   * the design is JSON DSL for v1 — inert at rest (avoids
   * Cypher-injection blast radius), schema-validatable client-side.
   * Stored as an opaque String here; validation happens in
   * {@code TemplateService} on save.
   */
  @Property("body")
  private String body;

  /**
   * Optional human-readable description — what the template is for,
   * when an author should pick it. Markdown-rendered in the picker
   * UI (post-T1d).
   */
  @Property("description")
  private String description;

  /**
   * Free-form author-supplied tags — {@code ["lumen", "rocket",
   * "calibration"]} on a hot-fire test recipe, say. Used by the
   * picker for filtering.
   */
  @Property("tags")
  private List<String> tags = new ArrayList<>();

  /** Username of the admin who created (or copy-on-write-edited) the row. */
  @Property("createdBy")
  private String createdBy;

  /** Millis since epoch when the row was created. */
  @Property("createdAt")
  private Long createdAt;

  /** Millis since epoch when the row was last touched. */
  @Property("updatedAt")
  private Long updatedAt;

  /**
   * When {@code true}, the template is filtered out of picker
   * listings. Set automatically by the copy-on-write supersede
   * machinery; an admin can also retire a template directly via
   * the {@code DELETE /v2/templates/{appId}} endpoint (T1b — soft-
   * delete, not hard).
   */
  @Property("retired")
  private boolean retired = false;

  public ShepardTemplate(String name, String templateKind, String body) {
    this.name = name;
    this.templateKind = templateKind;
    this.body = body;
    this.version = 1;
  }

  /** For testing purposes only. */
  public ShepardTemplate(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ShepardTemplate other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
