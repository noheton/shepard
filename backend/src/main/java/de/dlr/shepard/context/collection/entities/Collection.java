package de.dlr.shepard.context.collection.entities;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.neo4j.entities.AbstractDataObject;
import de.dlr.shepard.common.neo4j.entities.HasPermissions;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.data.file.entities.FileContainer;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class Collection extends AbstractDataObject implements HasPermissions {

  /**
   * Optional hero/banner image URL displayed at the top of the Collection
   * detail page. Nullable — when null, no banner is shown.
   *
   * <p>Additive nullable field; no Neo4j migration is needed. Existing
   * {@code :Collection} nodes without this property are read as {@code null}
   * by Spring Data Neo4j OGM. See migration note
   * {@code V54__NOOP_heroImageUrl_additive.cypher}.
   *
   * <p>URL-only for now (no server-side upload). Validation of reachability
   * is intentionally deferred to the frontend {@code <v-img>} error slot.
   * Exposed only on the {@code /v2/} surface — the legacy
   * {@code /shepard/api/} endpoints are unaffected.
   */
  private String heroImageUrl;

  /**
   * Optional free-text label describing the origin of this Collection,
   * e.g. "tapelaying", "bridgewelding", "v15-redrive-1". Intended to
   * disambiguate multiple Collections with the same {@code name} that
   * were created by successive import runs (NEO-AUDIT-007).
   *
   * <p>Additive nullable field; no Neo4j migration is needed. Existing
   * {@code :Collection} nodes without this property are read as {@code null}
   * by Spring Data Neo4j OGM.
   *
   * <p>Exposed only on the {@code /v2/} surface — the legacy
   * {@code /shepard/api/} endpoints are unaffected (see
   * {@code CollectionIO} {@code @JsonInclude(NON_NULL)} note).
   */
  private String importedFrom;

  /**
   * COLL-SCENE-1 — appId of a {@code :DigitalTwinScene} that renders as
   * the Collection's hero scene-graph (e.g. the MFFD robot cell). Nullable;
   * when null, the landing page shows no scene-graph viewer. The link is
   * intentionally a scalar app-level pointer rather than an OGM relationship
   * so this field stays loose-coupled with the v2 scene-graph package — the
   * permission walk on the scene side (SCENEGRAPH-PERMS-1) remains the
   * source-of-truth for who can read the scene; the link merely surfaces
   * a render affordance on the Collection's detail page.
   *
   * <p>Additive nullable field; no Neo4j migration is needed. Existing
   * {@code :Collection} nodes without this property are read as {@code null}
   * by Spring Data Neo4j OGM. See {@code V102__Collection_scene_graph_link.cypher}
   * for the operator-runbook NOOP migration note.
   *
   * <p>Exposed only on the {@code /v2/} surface — the legacy
   * {@code /shepard/api/} endpoints are unaffected (see
   * {@code CollectionIO} {@code @JsonInclude(NON_NULL)} note).
   *
   * <p>See {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md} GAP-6.
   */
  private String sceneGraphAppId;

  /**
   * PROMPT-h2 — controls how the PromptLog substrate stores conversation
   * bodies for AI interactions scoped to this Collection.
   *
   * <p>Valid values are the {@link PromptLogMode} enum names:
   * {@code "HASH_ONLY"} (default, safest — only a SHA-256 hash of the body
   * is stored), {@code "BODY_REDACTED"} (body stored after PII redaction at
   * ingest), or {@code "BODY_RAW"} (body stored verbatim — only for
   * air-gapped / EU AI Act Article 53 GPAI documentation deployments).
   *
   * <p>Stored as a String to avoid OGM enum-serialisation friction; the
   * {@link PromptLogMode} enum is the validation reference. Validated at the
   * IO layer only — the server stays permissive for additive forward compat.
   *
   * <p>Additive nullable field; the {@code V91} Cypher migration backfills
   * existing Collections with {@code "HASH_ONLY"}. Reading {@code null}
   * (pre-migration nodes) is treated as {@code "HASH_ONLY"} by all consumers.
   *
   * <p>Exposed only on the {@code /v2/} surface — the legacy
   * {@code /shepard/api/} endpoints are unaffected (see
   * {@code CollectionIO} {@code @JsonInclude(NON_NULL)} note).
   *
   * <p>See {@code aidocs/semantics/99-promptlog-design.md §10-11}.
   */
  private String promptLogMode;

  @Relationship(type = Constants.HAS_DATAOBJECT)
  private List<DataObject> dataObjects = new ArrayList<>();

  @Relationship(type = Constants.POINTS_TO, direction = Direction.INCOMING)
  private List<CollectionReference> incoming = new ArrayList<>();

  @Relationship(type = Constants.HAS_PERMISSIONS)
  private Permissions permissions;

  @Relationship(type = Constants.HAS_DEFAULT_FILE_CONTAINER)
  private FileContainer fileContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public Collection(long id) {
    super(id);
  }

  /**
   * Add one related DataObject
   *
   * @param dataObject the dataObject to add
   */
  public void addDataObject(DataObject dataObject) {
    dataObjects.add(dataObject);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObjects);
    result = prime * result + HasId.hashcodeHelper(incoming);
    result = prime * result + HasId.hashcodeHelper(permissions);
    result = prime * result + HasId.hashcodeHelper(version);
    result = prime * result + HasId.hashcodeHelper(fileContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof Collection)) return false;
    Collection other = (Collection) obj;
    return (
      HasId.areEqualSetsByUniqueId(dataObjects, other.dataObjects) &&
      HasId.areEqualSetsByUniqueId(incoming, other.incoming) &&
      HasId.equalsHelper(permissions, other.permissions) &&
      HasId.equalsHelper(version, other.version) &&
      HasId.equalsHelper(fileContainer, other.fileContainer)
    );
  }
}
