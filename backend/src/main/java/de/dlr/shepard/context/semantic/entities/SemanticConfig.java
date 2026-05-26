package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * N1c2 — single-instance Neo4j node carrying the runtime-mutable
 * ontology-preseed knobs (per the A3b
 * {@code :FeatureToggleRegistry} pattern + {@code aidocs/65 §2.2}).
 *
 * <p>The class layer enforces "exactly one" via
 * {@code OntologyConfigService.loadSingleton()} — first start seeds
 * the row from the deploy-time {@code shepard.semantic.internal.preseed-ontologies.*}
 * defaults; subsequent reads return the same row; subsequent writes
 * mutate it in place. The constraint on disk (V27) is L2a appId
 * uniqueness, not a singleton lock, so the invariant is "service-
 * level: every helper that touches the node reads/writes through
 * the same finder".
 *
 * <p>The {@code disabledBundles} set is the runtime-disabled bundle
 * ids. A bundle id present here AND in the manifest's
 * {@code required: true} subset is honoured as required (i.e. ignored
 * — required wins); see {@code OntologySeedService.shouldSeed}.
 * The deploy-time
 * {@code shepard.semantic.internal.preseed-ontologies.skip-bundles}
 * CSV is the install-time default and is set-unioned with this set
 * at seed time.
 *
 * @see de.dlr.shepard.context.semantic.services.OntologyConfigService
 * @see de.dlr.shepard.context.semantic.OntologySeedService
 */
@NodeEntity
@Data
@NoArgsConstructor
public class SemanticConfig implements HasId, HasAppId {

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
   * Master toggle — when false, the ontology pre-seed pass on
   * startup is a no-op (even when the deploy-time
   * {@code shepard.semantic.internal.preseed-ontologies.enabled=true}
   * default is set). Mirrors the A3b runtime-wins precedence.
   * Default: true.
   */
  @Property("preseedEnabled")
  private boolean preseedEnabled = true;

  /**
   * Bundle ids the operator has runtime-disabled. Stored as a
   * {@code List} (the OGM serialises this to a string[]) rather than
   * a {@code Set} because Neo4j OGM's default-property mapping is
   * order-preserving on arrays — useful for audit-trail reads.
   * Service-layer reads dedupe on the boundary.
   *
   * <p>A bundle id in this list whose manifest entry has
   * {@code required: true} is still seeded — required wins over
   * runtime disable.
   */
  @Property("disabledBundles")
  private List<String> disabledBundles = new ArrayList<>();

  /** Millis since epoch when the row was first persisted. */
  @Property("createdAt")
  private Long createdAt;

  /** Millis since epoch when {@code preseedEnabled} / {@code disabledBundles} was last touched. */
  @Property("updatedAt")
  private Long updatedAt;

  /** Username of the admin who last modified the config. */
  @Property("updatedBy")
  private String updatedBy;

  // ─── SEMA-V6-003 fields ──────────────────────────────────────────────────

  /**
   * SEMA-V6-003 — {@code appId} of the {@link Vocabulary} node that should
   * be pre-selected in the annotation dialog when a user creates a new annotation
   * without an explicit vocabulary context. Nullable; null means "no default".
   */
  @Property("defaultVocabularyAppId")
  private String defaultVocabularyAppId;

  /**
   * SEMA-V6-003 — annotation validation mode.
   * <ul>
   *   <li>{@code "STRICT"} — every annotation must use a predicate that exists
   *       in a registered {@link Vocabulary}; free-form annotations are rejected.</li>
   *   <li>{@code "PERMISSIVE"} (default) — free-form annotations are allowed;
   *       vocabulary-backed annotations are preferred but not required.</li>
   * </ul>
   * Stored as a plain string so schema evolution is safe.
   */
  @Property("annotationMode")
  private String annotationMode = "PERMISSIVE";

  /**
   * SEMA-V6-003 — when {@code true}, the AI suggestion flow
   * (SEMA-V6-004 / AI1h) is enabled and the "Suggest annotations" button
   * is shown in the annotation dialog. Default: {@code false}.
   */
  @Property("suggestionEnabled")
  private boolean suggestionEnabled = false;

  /**
   * SEMA-V6-003 — identifier of the AI model used for annotation suggestions
   * (e.g. {@code "saia-llm-default"}, {@code "openai-gpt-4o"}).
   * Only meaningful when {@link #suggestionEnabled} is {@code true}.
   * Nullable; null means "use the server default model".
   */
  @Property("suggestionModelId")
  private String suggestionModelId;

  // ─── SEMA-V6-014 fields ──────────────────────────────────────────────────

  /**
   * SEMA-V6-014 — when {@code true}, authenticated users may mint personal
   * vocabularies at {@code POST /v2/vocabularies/personal}. Default: {@code false}
   * (opt-in; operator enables once researchers need ad-hoc namespaces).
   */
  @Property("personalVocabulariesEnabled")
  private boolean personalVocabulariesEnabled = false;

  // ─── SEMA-V6-013 fields ──────────────────────────────────────────────────

  /**
   * SEMA-V6-013 — operator-configurable delete-policy for annotations.
   * <ul>
   *   <li>{@code "author-or-manager"} (default) — the annotation author OR any collection
   *       manager may delete the annotation.</li>
   *   <li>{@code "author-only"} — only the annotation author may delete; managers are blocked.</li>
   *   <li>{@code "manager-only"} — only collection managers may delete; authors who lack
   *       manager access are blocked.</li>
   * </ul>
   * Nullable; null is treated as {@code "author-or-manager"} (the default).
   */
  @Property("annotationDeletePolicy")
  private String annotationDeletePolicy;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
