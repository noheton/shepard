package de.dlr.shepard.common.util;

import de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import de.dlr.shepard.common.search.endpoints.BasicContainerAttributes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CypherQueryHelper {

  public enum Neighborhood {
    EVERYTHING,
    OUTGOING,
    ESSENTIAL,
  }

  private CypherQueryHelper() {}

  public static String getObjectPartWithVersion(String variable, String type, boolean hasName, String versionVariable) {
    String ret = getObjectPart(variable, type, hasName);
    ret = ret + "-[:has_version]->(" + versionVariable + ":Version)";
    return ret;
  }

  public static String getObjectPart(String variable, String type, boolean hasName) {
    if (hasName) return getObjectPartWithName(variable, type);
    else return getObjectPartWithoutName(variable, type);
  }

  private static String getObjectPartWithName(String variable, String type) {
    var namePart = "{ name : $name, deleted: FALSE }";
    var result = "(%s:%s %s)".formatted(variable, type, namePart);
    return result;
  }

  private static String getObjectPartWithoutName(String variable, String type) {
    var namePart = "{ deleted: FALSE }";
    var result = "(%s:%s %s)".formatted(variable, type, namePart);
    return result;
  }

  public static String getPaginationPart() {
    return "SKIP $offset LIMIT $size";
  }

  public static String getPaginationPart(PaginationHelper paginationParams) {
    return "SKIP %d LIMIT %d".formatted(paginationParams.getOffset(), paginationParams.getSize());
  }

  public static String getReturnPart(String entity) {
    return getReturnPart(entity, Neighborhood.EVERYTHING, 1);
  }

  public static String getReturnPart(String entity, int depth) {
    return getReturnPart(entity, Neighborhood.EVERYTHING, depth);
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood) {
    return getReturnPart(entity, neighborhood, 1);
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, PaginationHelper pagination) {
    return getReturnPart(entity, neighborhood, 1, pagination);
  }

  public static String getReturnCountPart(String entity, Neighborhood neighborhood) {
    return (getNeighborhoodPart(entity, neighborhood, 1) + " RETURN " + "COUNT(%s)".formatted(entity));
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, int depth) {
    return (
      getNeighborhoodPart(entity, neighborhood, depth) +
      " RETURN " +
      "%s, nodes(path), relationships(path)".formatted(entity)
    );
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, int depth, PaginationHelper pagination) {
    return (
      getNeighborhoodPart(entity, neighborhood, depth) +
      (pagination != null ? " " + CypherQueryHelper.getPaginationPart(pagination) : "") +
      " RETURN " +
      "%s, nodes(path), relationships(path)".formatted(entity)
    );
  }

  private static String getNeighborhoodPart(String entity, Neighborhood neighborhood, int depth) {
    // Clamp the depth between 1 and 3 nodes
    depth = Math.max(1, Math.min(3, depth));
    String match =
      switch (neighborhood) {
        case EVERYTHING -> "path=(%s)-[*0..%d]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL";
        case OUTGOING -> "path=(%s)-[*0..%d]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL";
        case ESSENTIAL -> "path=(%s)-[*0..%d]->(n) WHERE n:Permission OR n:User";
      };
    return "MATCH " + match.formatted(entity, depth);
  }

  public static String getReturnPartLight(String entity) {
    return "RETURN " + entity;
  }

  /**
   * DATAOBJECT-LIST-ON2 — list-specific depth-1 neighborhood return that
   * <strong>excludes the two fan-out edges that make OGM entity mapping
   * O(n²)</strong>: the shared {@code :Collection} back-edge
   * ({@code has_dataobject}) and the per-DataObject {@code has_reference} edge.
   *
   * <p>The default {@link #getReturnPart(String)} walks {@code (d)-[*0..1]-(n)}
   * undirected. Neo4j-OGM then populates each hydrated one-to-many collection by
   * merging every returned path row into it with an {@code ArrayList.indexOf}
   * dedup ({@code EntityAccessManager.coerceCollection}) — quadratic in the size
   * of that collection. Two collections blow up at scale:
   * <ul>
   *   <li><b>{@code Collection.dataObjects}</b> — every DataObject in the list
   *       re-discovers its incoming {@code has_dataobject} edge to the single
   *       shared {@code :Collection} node, so a collection with N DataObjects is
   *       O(N²).</li>
   *   <li><b>{@code DataObject.references}</b> — a single DataObject holding K
   *       references (the live MFFD-Dropbox "Tapelaying" DataObject holds
   *       <b>102,953</b> FileReferences) makes hydrating that one row O(K²). This
   *       is the actual 2026-07-19 jstack spiral: {@code mapOneToMany →
   *       coerceCollection} on {@code d.references}, confirmed against live Neo4j
   *       (the collection has only 2 DataObjects, so {@code Collection.dataObjects}
   *       cannot be the term).</li>
   * </ul>
   *
   * <p>Excluding both edges keeps every OTHER depth-1 relationship the list IO
   * needs (successors, predecessors, children, parent, incoming
   * DataObjectReferences, created/updated-by) while dropping only the two
   * fan-out edges, so hydrating a DataObject row is O(1) regardless of how many
   * references it holds or how large the collection is. The caller is
   * responsible for cheaply re-attaching:
   * <ul>
   *   <li>the (already-loaded, light) parent Collection, so
   *       {@code DataObjectIO.collectionId} resolves;</li>
   *   <li>lightweight reference stubs (via a scalar {@code collect} projection),
   *       so {@code DataObjectIO.referenceIds} + the per-kind counts stay
   *       byte-compatible on the frozen v1 surface.</li>
   * </ul>
   * See {@code DataObjectService.getAllDataObjectsByShepardIds}.
   *
   * <p>The {@code NONE(rel IN relationships(path) ...)} guard preserves the
   * zero-length path (the DataObject itself, whose {@code relationships(path)}
   * is empty) so {@code d} is always returned.
   *
   * @param entity the Cypher variable bound to the DataObject rows
   * @return a {@code MATCH path=... RETURN entity, nodes(path), relationships(path)}
   *         clause that never traverses a {@code has_dataobject} or
   *         {@code has_reference} edge
   */
  public static String getReturnPartForList(String entity) {
    return (
      "MATCH path=(" +
      entity +
      ")-[*0..1]-(n) WHERE (n.deleted = FALSE OR n.deleted IS NULL) AND NONE(rel IN relationships(path) WHERE type(rel) = '" +
      Constants.HAS_DATAOBJECT +
      "' OR type(rel) = '" +
      Constants.HAS_REFERENCE +
      "') RETURN " +
      entity +
      ", nodes(path), relationships(path)"
    );
  }

  /**
   * GETDO-DETAIL-ON2 + GETDO-DETAIL-TARGETED — single-DataObject <em>detail</em>
   * depth-1 neighborhood return that hydrates every structural edge the detail view
   * needs while <strong>never touching</strong> the {@code has_reference} fan-out edge.
   *
   * <p><b>DataObject-specific.</b> Every current caller applies this to a
   * {@code :DataObject} node — {@code VersionableEntityDAO.findByShepardIdForDetail}
   * (only wired for DataObject detail) and the three container DAOs'
   * {@code loadLinkedDataObjectForPanel} (which load a linked DataObject for the
   * "referenced-by" panel). The positive allowlist below is the DataObject class
   * hierarchy's declared {@code @Relationship} set; do NOT reuse this for a non-
   * DataObject entity (its edges would be silently dropped).
   *
   * <p><b>Why a positive allowlist, not a negative filter (GETDO-DETAIL-TARGETED).</b>
   * GETDO-DETAIL-ON2 originally shipped the safe negative form
   * {@code (o)-[*0..1]-(n) WHERE NONE(rel WHERE type(rel)='has_reference')}. That
   * form has no relationship-type restriction in the pattern, so Neo4j's
   * {@code VarLengthExpand} enumerates <em>every</em> incident edge — including all
   * 258k+ {@code has_reference} edges on the MFFD Tapelaying DO — and only then
   * discards them in the {@code NONE(...)} post-filter: O(K) db-hits per detail open
   * (PROFILE: 259,406 db-hits on the neighborhood, 309,839 on the full detail query).
   * Pushing the wanted types <em>into</em> the pattern
   * ({@code (o)-[:type1|...|typeN*0..1]-(n)}) lets the expand skip the dense-node
   * {@code has_reference} relationship group entirely: O(1) in reference degree
   * (PROFILE: 599 db-hits on the neighborhood — a 99.8% reduction — flat across a
   * 637-ref DO and the 258k-ref DO).
   *
   * <p><b>Equivalence (byte-compat).</b> Neo4j-OGM only maps relationship types that
   * correspond to a declared {@code @Relationship} field, so returning exactly the
   * declared types (minus {@code has_reference}) yields an OGM-hydrated entity
   * <em>identical</em> to the negative form's. Live-verified on the Tapelaying DO: the
   * negative form additionally returned {@code created_in_month} (NEO-AUDIT-004 index
   * edge) and {@code has_permissions} — both OGM-<em>unmapped</em> on DataObject, so
   * dropping them changes nothing in the mapped entity.
   *
   * <p>The allowlist = the declared {@code @Relationship} types across the DataObject
   * class chain, minus the excluded fan-out:
   * <ul>
   *   <li>{@code DataObject}: {@code has_dataobject} (collection, for the permission
   *       check), {@code has_successor} (successors + predecessors),
   *       {@code has_child} (children + parent), {@code points_to} (incoming
   *       DataObjectReferences), {@code has_labjournalentry}</li>
   *   <li>{@code BasicEntity}: {@code has_annotation}</li>
   *   <li>{@code VersionableEntity}: {@code has_version}</li>
   *   <li>{@code AbstractEntity}: {@code created_by}, {@code updated_by}</li>
   *   <li><b>excluded:</b> {@code has_reference} (the supernode fan-out)</li>
   * </ul>
   *
   * <p>Reference data is re-attached out-of-band: v2 detail sources it from
   * {@code DataObjectDAO.findContainersByDataObjectAppId} (Cypher, bounded) and
   * {@code @JsonIgnore}s the legacy {@code referenceIds}/counts; v1 detail reconstructs
   * {@code referenceIds}+counts via a scalar projection (byte-compat), same as the list
   * fix. See {@code DataObjectService.getDataObject(..., reconstructReferences)}.
   *
   * @param entity the Cypher variable bound to the DataObject
   * @return a {@code MATCH path=... RETURN entity, nodes(path), relationships(path)}
   *         clause whose expand only traverses the DataObject's declared, non-fan-out
   *         edge types (so a {@code has_reference} supernode is never walked)
   */
  public static String getReturnPartForDetail(String entity) {
    // Positive edge-type allowlist = the DataObject class hierarchy's declared
    // @Relationship types MINUS has_reference (the supernode fan-out). Built from
    // Constants so it stays in lockstep with the entity definitions. See javadoc for
    // the field-by-field mapping and the O(K)→O(1) rationale (GETDO-DETAIL-TARGETED).
    String allowedTypes = String.join(
      "|",
      Constants.HAS_DATAOBJECT,
      Constants.HAS_SUCCESSOR,
      Constants.HAS_CHILD,
      Constants.POINTS_TO,
      Constants.HAS_LABJOURNAL_ENTRY,
      Constants.HAS_ANNOTATION,
      Constants.HAS_VERSION,
      Constants.CREATED_BY,
      Constants.UPDATED_BY
    );
    return (
      "MATCH path=(" +
      entity +
      ")-[:" +
      allowedTypes +
      "*0..1]-(n) WHERE (n.deleted = FALSE OR n.deleted IS NULL) RETURN " +
      entity +
      ", nodes(path), relationships(path)"
    );
  }

  /**
   * SUPERNODE-F2-COLLECTION-DETAIL — Collection <em>detail/list</em> depth-1
   * neighborhood return that excludes <strong>only</strong> the
   * {@code has_dataobject} fan-out edge.
   *
   * <p>Sibling of {@link #getReturnPartForList(String)} (DataObject list) and
   * {@link #getReturnPartForDetail(String)} (DataObject detail), but for the
   * {@code :Collection} root. The one edge that makes a Collection a supernode is
   * {@code has_dataobject}: the live {@code mffd-afp-tapelaying} Collection carries
   * <b>8,483</b> {@code has_dataobject} edges, so the default
   * {@link #getReturnPart(String)} (undirected {@code (c)-[*0..1]-(n)},
   * {@code Neighborhood.EVERYTHING}) hydrates all 8,483 contained DataObjects into
   * OGM on every collection-detail open — the single most-visited surface in the
   * app — and each row re-discovers its incoming {@code has_dataobject} edge to the
   * shared {@code :Collection} node, spiralling to O(N²) in
   * {@code EntityAccessManager.coerceCollection} (the same {@code ArrayList.indexOf}
   * dedup landmine documented on {@link #getReturnPartForList(String)}).
   *
   * <p>The collection-detail response needs the Collection's own scalars plus its
   * <em>bounded</em> structural edges — {@code has_permissions}, {@code has_version},
   * {@code has_default_file_container} (so {@code defaultFileContainerAppId}
   * populates), {@code created_by}/{@code updated_by}, and any incoming
   * {@code CollectionReference}s — but NOT the O(N) member list. The contained
   * DataObjects are served separately by the paged
   * {@code GET /v2/collections/{appId}/data-objects} endpoint, and the v2 response
   * shape ({@code CollectionV2IO}) already {@code @JsonIgnore}s
   * {@code dataObjectIds}/{@code incomingIds} — so excluding {@code has_dataobject}
   * here is <em>wire-identical</em> on the v2 surface (no id reconstruction needed,
   * unlike the DataObject list/detail fixes).
   *
   * <p>Excludes ONLY {@code has_dataobject} — every other depth-1 edge is kept, so a
   * Collection row hydrates in O(1) regardless of how many DataObjects it holds. The
   * v1 {@code /shepard/api/} surface (whose {@code CollectionIO.dataObjectIds} is
   * {@code required=true}) must NOT use this return-part; its loaders stay on the
   * members-hydrating {@link #getReturnPart(String)}.
   *
   * <p>The {@code NONE(rel IN relationships(path) ...)} guard preserves the
   * zero-length path (the Collection itself) so {@code entity} is always returned.
   *
   * @param entity the Cypher variable bound to the Collection
   * @return a {@code MATCH path=... RETURN entity, nodes(path), relationships(path)}
   *         clause that never traverses a {@code has_dataobject} edge
   */
  public static String getReturnPartForCollectionDetail(String entity) {
    return (
      "MATCH path=(" +
      entity +
      ")-[*0..1]-(n) WHERE (n.deleted = FALSE OR n.deleted IS NULL) AND NONE(rel IN relationships(path) WHERE type(rel) = '" +
      Constants.HAS_DATAOBJECT +
      "') RETURN " +
      entity +
      ", nodes(path), relationships(path)"
    );
  }

  public static String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
    String ret;
    boolean isString = orderByAttribute.isString();
    if (!isString) ret = "ORDER BY " + variable + "." + orderByAttribute;
    else if (
      orderByAttribute instanceof BasicContainerAttributes attributes && attributes == BasicContainerAttributes.type
    ) ret = "ORDER BY LABELS(" + variable + ")";
    else ret = "ORDER BY toLower(" + variable + "." + orderByAttribute + ")";
    if (orderByAttribute.toString() == "id") ret = "ORDER BY id(" + variable + ")";
    if (orderDesc != null && orderDesc) ret = ret + " DESC";
    return ret;
  }

  public static String getShepardIdPart(String variable, long shepardId) {
    return variable + "." + Constants.SHEPARD_ID + " = " + shepardId;
  }

  public static String getShepardIdsPart(String variable, List<Long> shepardIds) {
    String commaSeparatedIds = shepardIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    return variable + "." + Constants.SHEPARD_ID + " in [" + commaSeparatedIds + "]";
  }

  public static String getReadableByQuery(String variable, String username) {
    String ret =
      """
      (NOT exists((%s)-[:has_permissions]->(:Permissions)) \
      OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "%s" })) \
      OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "%s"})))""".formatted(
          variable,
          variable,
          username,
          variable,
          variable,
          variable,
          username
        );
    return ret;
  }

  public static String getVersionHeadPart(String variable) {
    if (VersioningFeatureToggle.isEnabled()) {
      return "(" + variable + ".isHEADVersion = true)";
    }
    return "(1=1)";
  }

  public static String getVersionPart(String variable, UUID versionUID) {
    return "(" + variable + ".uid = '" + versionUID + "')";
  }
}
