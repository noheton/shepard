package de.dlr.shepard.util;

import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;

public class CypherQueryHelper {

	private CypherQueryHelper() {
	}

	public static String getObjectPart(String variable, String type, boolean hasName) {
		if (hasName)
			return getObjectPartWithName(variable, type);
		else
			return getObjectPartWithoutName(variable, type);
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
		return getReturnPart(entity, false);
	}

	public static String getReturnPart(String entity, boolean omitIncoming) {
		var baseString = omitIncoming
				? "MATCH path=(%s)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN %s, nodes(path), relationships(path)"
				: "MATCH path=(%s)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN %s, nodes(path), relationships(path)";
		var result = String.format(baseString, entity, entity);
		return result;
	}

	public static String getOrderByPart(String variable, OrderByAttribute orderByAttribute, Boolean orderDesc) {
		String ret;
		boolean isString = orderByAttribute.isString();
		if (!isString)
			ret = "ORDER BY " + variable + "." + orderByAttribute;
		else
			ret = "ORDER BY toLower(" + variable + "." + orderByAttribute + ")";
		if (orderDesc != null && orderDesc)
			ret = ret + " DESC";
		return ret;
	}

	public static String getReadableByQuery(String variable, String username) {
		String ret = String.format(
				"""
						(NOT exists((%s)-[:has_permissions]->(:Permissions)) \
						OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by|owned_by]->(:User { username: "%s" })) \
						OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "Public"})) \
						OR exists((%s)-[:has_permissions]->(:Permissions {permissionType: "PublicReadable"})) \
						OR exists((%s)-[:has_permissions]->(:Permissions)-[:readable_by_group]->(:UserGroup)<-[:is_in_group]-(:User { username: "%s"})))""",
				variable, variable, username, variable, variable, variable, username);
		return ret;
	}
}
