package de.dlr.shepard.util;

import de.dlr.shepard.configuration.feature.toggles.VersioningFeatureToggle;
import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import java.util.UUID;

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

  public static String getReturnPart(String entity) {
    return getReturnPart(entity, Neighborhood.EVERYTHING, 1);
  }

  public static String getReturnPart(String entity, int depth) {
    return getReturnPart(entity, Neighborhood.EVERYTHING, depth);
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood) {
    return getReturnPart(entity, neighborhood, 1);
  }

  public static String getReturnPart(String entity, Neighborhood neighborhood, int depth) {
    // Clamp the depth between 1 and 3 nodes
    depth = Math.max(1, Math.min(3, depth));
    String match =
      switch (neighborhood) {
        case EVERYTHING -> "path=(%s)-[*0..%d]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL";
        case OUTGOING -> "path=(%s)-[*0..%d]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL";
        case ESSENTIAL -> "path=(%s)-[*0..%d]->(n) WHERE n:Permission OR n:User";
      };
    var result =
      "MATCH " +
      String.format(match, entity, depth) +
      " RETURN " +
      String.format("%s, nodes(path), relationships(path)", entity);
    return result;
  }

  public static String getReturnPartLight(String entity) {
    return "RETURN " + entity;
  }

  public static String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
    String ret;
    boolean isString = orderByAttribute.isString();
    if (!isString) ret = "ORDER BY " + variable + "." + orderByAttribute;
    else ret = "ORDER BY toLower(" + variable + "." + orderByAttribute + ")";
    if (orderDesc != null && orderDesc) ret = ret + " DESC";
    return ret;
  }

  public static String getShepardIdPart(String variable, long shepardId) {
    return variable + "." + Constants.SHEPARD_ID + " = " + shepardId;
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
      return "(NOT exists ((" + variable + ")<-[:has_predecessor]-(:Version)))";
    }
    return "(1=1)";
  }

  public static String getVersionPart(String variable, UUID versionUID) {
    return "(" + variable + ".uid = '" + versionUID + "')";
  }
}
