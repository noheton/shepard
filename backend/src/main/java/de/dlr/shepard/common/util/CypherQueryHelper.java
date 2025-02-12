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
    var result = String.format("(%s:%s %s)", variable, type, namePart);
    return result;
  }

  private static String getObjectPartWithoutName(String variable, String type) {
    var namePart = "{ deleted: FALSE }";
    var result = String.format("(%s:%s %s)", variable, type, namePart);
    return result;
  }

  public static String getPaginationPart() {
    return "SKIP $offset LIMIT $size";
  }

  public static String getPaginationPart(PaginationHelper paginationParams) {
    return String.format("SKIP %d LIMIT %d", paginationParams.getOffset(), paginationParams.getSize());
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
    return (getNeighborhoodPart(entity, neighborhood, 1) + " RETURN " + String.format("COUNT(%s)", entity));
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, int depth) {
    return (
      getNeighborhoodPart(entity, neighborhood, depth) +
      " RETURN " +
      String.format("%s, nodes(path), relationships(path)", entity)
    );
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, int depth, PaginationHelper pagination) {
    return (
      getNeighborhoodPart(entity, neighborhood, depth) +
      (pagination != null ? " " + CypherQueryHelper.getPaginationPart(pagination) : "") +
      " RETURN " +
      String.format("%s, nodes(path), relationships(path)", entity)
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
    return "MATCH " + String.format(match, entity, depth);
  }

  public static String getReturnPartLight(String entity) {
    return "RETURN " + entity;
  }

  public static String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
    String ret;
    boolean isString = orderByAttribute.isString();
    if (!isString) ret = "ORDER BY " + variable + "." + orderByAttribute;
    else if (
      orderByAttribute instanceof BasicContainerAttributes &&
      ((BasicContainerAttributes) orderByAttribute) == BasicContainerAttributes.type
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
    String ret = String.format(
      """
      (NOT exists((%s)-[:has_permissions]->(:Permissions)) \
      OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "%s" })) \
      OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
      OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
      OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "%s"})))""",
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
