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
   * GETDO-DETAIL-ON2 — single-DataObject <em>detail</em> depth-1 neighborhood return
   * that excludes <strong>only</strong> the {@code has_reference} fan-out edge.
   *
   * <p>Sibling of {@link #getReturnPartForList(String)} but for the detail path.
   * Unlike the list, a single DataObject's detail view genuinely needs its bounded
   * structural edges hydrated — the parent {@code :Collection} ({@code has_dataobject},
   * which the detail service reads via {@code d.getCollection().getShepardId()} for
   * the permission check), plus successors / predecessors / children / version. So we
   * keep {@code has_dataobject} (a single incoming edge here, not the O(N) collection
   * fan-out the list feared) and drop ONLY {@code has_reference} — the edge that on a
   * large-fanout DataObject (the MFFD Tapelaying DO holds 177k+ FileReferences) makes
   * OGM's {@code coerceCollection} ({@code ArrayList.indexOf} dedup) O(K²) and pegs the
   * backend for minutes, rendering the detail page unopenable.
   *
   * <p>Reference data is re-attached out-of-band: v2 detail sources it from
   * {@code DataObjectDAO.findContainersByDataObjectAppId} (Cypher, bounded) and
   * {@code @JsonIgnore}s the legacy {@code referenceIds}/counts; v1 detail reconstructs
   * {@code referenceIds}+counts via a scalar projection (byte-compat), same as the list
   * fix. See {@code DataObjectService.getDataObject(..., reconstructReferences)}.
   *
   * @param entity the Cypher variable bound to the DataObject
   * @return a {@code MATCH path=... RETURN entity, nodes(path), relationships(path)}
   *         clause that never traverses a {@code has_reference} edge
   */
  public static String getReturnPartForDetail(String entity) {
    return (
      "MATCH path=(" +
      entity +
      ")-[*0..1]-(n) WHERE (n.deleted = FALSE OR n.deleted IS NULL) AND NONE(rel IN relationships(path) WHERE type(rel) = '" +
      Constants.HAS_REFERENCE +
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
