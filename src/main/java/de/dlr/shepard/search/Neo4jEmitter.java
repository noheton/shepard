package de.dlr.shepard.search;

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.TraversalRules;

public class Neo4jEmitter {

	private static final String FILECONTAINER_IN_QUERY = "fc";
	private static final String TIMESERIESCONTAINER_IN_QUERY = "tsc";
	private static final String STRUCTUREDDATACONTAINER_IN_QUERY = "sdc";

	private static final List<String> booleanOperators = List.of(Constants.JSON_AND, Constants.JSON_OR,
			Constants.JSON_NOT, Constants.JSON_XOR);

	private Neo4jEmitter() {
	}

	private static String emitNeo4j(String jsonquery, String variable) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			jsonNode = objectMapper.readValue(jsonquery, JsonNode.class);
		} catch (Exception e) {
			throw new ShepardParserException("could not parse JSON\n" + e.getMessage());
		}
		return emitNeo4j(jsonNode, variable);
	}

	private static String emitNeo4j(JsonNode rootNode, String variable) {
		String op = "";
		try {
			op = rootNode.fieldNames().next();
		} catch (Exception e) {
			throw new ShepardParserException("error in parsing" + e.getMessage());
		}
		if (op.equals(Constants.OP_PROPERTY) || op.equals(Constants.OP_VALUE) || op.equals(Constants.OP_OPERATOR)) {
			return emitPrimitiveClause(rootNode, variable);
		}
		return emitComplexClause(rootNode, op, variable);
	}

	private static String emitComplexClause(JsonNode node, String operator, String variable) {
		if (!booleanOperators.contains(operator))
			throw new ShepardParserException("unknown boolean operator: " + operator);
		if (operator.equals(Constants.JSON_NOT))
			return emitNotClause(node, variable);
		else
			return emitMultaryClause(node, operator, variable);
	}

	private static String emitMultaryClause(JsonNode node, String operator, String variable) {
		Iterator<JsonNode> argumentsArray = node.get(operator).elements();
		String firstArgument = emitNeo4j(argumentsArray.next(), variable);
		String ret = "(" + firstArgument;
		while (argumentsArray.hasNext()) {
			ret = ret + " " + operator + " " + emitNeo4j(argumentsArray.next(), variable);
		}
		ret = ret + ")";
		return ret;
	}

	private static String emitNotClause(JsonNode node, String variable) {
		JsonNode body = node.get(Constants.JSON_NOT);
		return "(NOT(" + emitNeo4j(body, variable) + "))";
	}

	private static String emitPrimitiveClause(JsonNode node, String variable) {
		String property = node.get(Constants.OP_PROPERTY).textValue();
		if (property.equals("createdBy") || property.equals("updatedBy"))
			return emitByPart(node, variable);
		// for CollectionReferences
		if (property.equals("referencedCollectionId"))
			return emitReferencedCollectionIdPart(node, variable);
		// for DataObjectReferences
		if (property.equals("referencedDataObjectId"))
			return emitReferencedDataObjectIdPart(node, variable);
		// for FileReferences
		if (property.equals("fileContainerId"))
			return emitFileContainerIdPart(node, variable);
		// for StructuredDataReferences
		if (property.equals("structuredDataContainerId"))
			return emitStructuredDataContainerIdPart(node, variable);
		// for TimeseriesaReferences
		if (property.equals("timeseriesContainerId"))
			return emitTimeseriesContainerIdPart(node, variable);
		String ret = "(";
		if (property.equals("id"))
			ret = ret + "id(" + variable + ")";
		else
			ret = ret + variable + ".`" + node.get(Constants.OP_PROPERTY).textValue() + "` ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		if (node.get(Constants.OP_OPERATOR).textValue().equals(Constants.JSON_IN)) {
			ret = ret + "[";
			Iterator<JsonNode> setArray = node.get(Constants.OP_VALUE).elements();
			if (setArray.hasNext()) {
				ret = ret + setArray.next();
				while (setArray.hasNext())
					ret = ret + ", " + setArray.next();
				ret = ret + "]";
			} else {
				ret = ret + "]";
			}
		} else
			ret = ret + node.get(Constants.OP_VALUE);
		ret = ret + ")";
		return ret;
	}

	private static String emitByPart(JsonNode node, String variable) {
		String ret = "(";
		String by = "";
		switch (node.get(Constants.OP_PROPERTY).textValue()) {
		case "createdBy":
			by = "created_by";
			break;
		case "updatedBy":
			by = "updated_by";
			break;
		default:
			// Ignore other properties
			break;
		}
		ret = ret + "EXISTS {MATCH (" + variable + ") - [:" + by + "] -> (u) WHERE u.username ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitTimeseriesContainerIdPart(JsonNode node, String variable) {
		String ret = "(";
		ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.IS_IN_CONTAINER
				+ "]->(refCon:TimeseriesContainer) WHERE id(refCon) ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitStructuredDataContainerIdPart(JsonNode node, String variable) {
		String ret = "(";
		ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.IS_IN_CONTAINER
				+ "]->(refCon:StructuredDataContainer) WHERE id(refCon) ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitFileContainerIdPart(JsonNode node, String variable) {
		String ret = "(";
		ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.IS_IN_CONTAINER
				+ "]->(refCon:FileContainer) WHERE id(refCon) ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitReferencedDataObjectIdPart(JsonNode node, String variable) {
		String ret = "(";
		ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO
				+ "]->(refDo:DataObject) WHERE id(refDo) ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitReferencedCollectionIdPart(JsonNode node, String variable) {
		String ret = "(";
		ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO
				+ "]->(refCol:Collection) WHERE id(refCol) ";
		ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
		ret = ret + node.get(Constants.OP_VALUE) + " ";
		ret = ret + "})";
		return ret;
	}

	private static String emitOperatorString(JsonNode node) {
		String operator = node.textValue();
		return switch (operator) {
		case Constants.JSON_EQ -> "=";
		case Constants.JSON_CONTAINS -> "contains";
		case Constants.JSON_GT -> ">";
		case Constants.JSON_LT -> "<";
		case Constants.JSON_GE -> ">=";
		case Constants.JSON_LE -> "<=";
		case Constants.JSON_IN -> "IN";
		case Constants.JSON_NE -> "<>";
		default -> throw new ShepardParserException("unknown comparison operator " + operator);
		};
	}

	private static String emitCollectionMatchPart() {
		String ret = "";
		ret = ret + "MATCH (" + Constants.COLLECTION_IN_QUERY + ":Collection)";
		return ret;
	}

	private static String emitFileContainerMatchPart() {
		String ret = "";
		ret = ret + "MATCH (" + FILECONTAINER_IN_QUERY + ":FileContainer)";
		return ret;
	}

	private static String emitTimeseriesContainerMatchPart() {
		String ret = "";
		ret = ret + "MATCH (" + TIMESERIESCONTAINER_IN_QUERY + ":TimeseriesContainer)";
		return ret;
	}

	private static String emitStructuredDataContainerMatchPart() {
		String ret = "";
		ret = ret + "MATCH (" + STRUCTUREDDATACONTAINER_IN_QUERY + ":StructuredDataContainer)";
		return ret;
	}

	private static String emitCollectionIdReturnPart() {
		String ret = " RETURN id(" + Constants.COLLECTION_IN_QUERY + ")";
		return ret;
	}

	private static String emitCollectionDataObjectMatchPart() {
		String ret = "MATCH (" + Constants.COLLECTION_IN_QUERY + ":Collection)-[:has_dataobject]->("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject) ";
		return ret;
	}

	private static String emitCollectionIdWherePart(Long collectionId) {
		String ret = "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + collectionId + ")";
		return ret;
	}

	private static String emitNotDeletedPart(String variable) {
		String ret = "(" + variable + ".deleted = FALSE)";
		return ret;
	}

	private static String emitCollectionDataObjectIdWherePart(Long collectionId, Long dataObjectId) {
		String ret = "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + collectionId + " AND id("
				+ Constants.DATAOBJECT_IN_QUERY + ") = " + dataObjectId + ")";
		return ret;
	}

	private static String emitCollectionDataObjectTraversalIdWherePart(Long collectionId, Long dataObjectId) {
		String ret = "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + collectionId + " AND id(d) = " + dataObjectId
				+ ")";
		return ret;
	}

	private static String emitDataObjectIdReturnPart() {
		String ret = " RETURN id(" + Constants.DATAOBJECT_IN_QUERY + ")";
		return ret;
	}

	private static String emitReadableByPart(String username) {
		String variable = Constants.COLLECTION_IN_QUERY;
		return CypherQueryHelper.getReadableByQuery(variable, username);
	}

	public static String emitCollectionQuery(String searchBodyQuery, String userName) {
		String ret = "";
		ret = ret + emitCollectionMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.COLLECTION_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.COLLECTION_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(userName);
		ret = ret + emitCollectionIdReturnPart();
		return ret;
	}

	public static String emitFileContainerQuery(String JSONQuery, String userName) {
		String ret = "";
		ret = ret + emitFileContainerMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(JSONQuery, FILECONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(FILECONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + CypherQueryHelper.getReadableByQuery(FILECONTAINER_IN_QUERY, userName);
		ret = ret + emitContainerReturnPart(FILECONTAINER_IN_QUERY);
		return ret;
	}

	public static String emitTimeseriesContainerQuery(String JSONQuery, String userName) {
		String ret = "";
		ret = ret + emitTimeseriesContainerMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(JSONQuery, TIMESERIESCONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(TIMESERIESCONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + CypherQueryHelper.getReadableByQuery(TIMESERIESCONTAINER_IN_QUERY, userName);
		ret = ret + emitContainerReturnPart(TIMESERIESCONTAINER_IN_QUERY);
		return ret;
	}

	public static String emitStructuredDataContainerQuery(String JSONQuery, String userName) {
		String ret = "";
		ret = ret + emitStructuredDataContainerMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(JSONQuery, STRUCTUREDDATACONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(STRUCTUREDDATACONTAINER_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + CypherQueryHelper.getReadableByQuery(STRUCTUREDDATACONTAINER_IN_QUERY, userName);
		ret = ret + emitContainerReturnPart(STRUCTUREDDATACONTAINER_IN_QUERY);
		return ret;
	}

	private static String emitContainerReturnPart(String containerVariable) {
		return " WITH " + containerVariable + " MATCH path=(" + containerVariable
				+ ")-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN " + containerVariable
				+ ", nodes(path), relationships(path)";
	}

	public static String emitCollectionDataObjectQuery(Long collectionId, String searchBodyQuery, String username) {
		String ret = "";
		ret = ret + emitCollectionDataObjectMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionIdWherePart(collectionId);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectIdReturnPart();
		return ret;
	}

	public static String emitCollectionDataObjectDataObjectQuery(SearchScope scope, TraversalRules traversalRule,
			String searchBodyQuery, String username) {
		String ret = "";
		ret = ret + emitCollectionDataObjectDataObjectMatchPart(traversalRule);
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionDataObjectTraversalIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectIdReturnPart();
		return ret;
	}

	public static String emitCollectionDataObjectDataObjectQuery(SearchScope scope, String searchBodyQuery,
			String username) {
		String ret = "";
		ret = ret + emitCollectionDataObjectMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionDataObjectIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectIdReturnPart();
		return ret;
	}

	public static String emitDataObjectQuery(String searchBodyQuery, String username) {
		String ret = "";
		ret = ret + emitCollectionDataObjectMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitCollectionDataObjectIdReturnPart();
		return ret;
	}

	private static String emitReferenceMatchPart() {
		String ret = "MATCH (" + Constants.COLLECTION_IN_QUERY + ":Collection)-[:has_dataobject]->("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject)-[:has_reference]->(" + Constants.REFERENCE_IN_QUERY
				+ ":BasicReference)";
		return ret;
	}

	public static String emitBasicReferenceQuery(String searchBodyQuery, String username) {
		String ret = "";
		ret = ret + emitReferenceMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitCollectionDataObjectReferenceIdReturnPart();
		return ret;
	}

	public static String emitCollectionBasicReferenceQuery(String searchBodyQuery, Long collectionId, String username) {
		String ret = "";
		ret = ret + emitReferenceMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionIdWherePart(collectionId);
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectReferenceIdReturnPart();
		return ret;
	}

	public static String emitCollectionDataObjectBasicReferenceQuery(SearchScope scope, TraversalRules traversalRule,
			String searchBodyQuery, String username) {
		String ret = "";
		ret = ret + emitCollectionDataObjectBasicReferenceMatchPart(traversalRule);
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionDataObjectTraversalIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectReferenceIdReturnPart();
		return ret;
	}

	public static String emitCollectionDataObjectReferenceQuery(SearchScope scope, String searchBodyQuery,
			String username) {
		String ret = "";
		ret = ret + emitReferenceMatchPart();
		ret = ret + " WHERE ";
		ret = ret + emitNeo4j(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitCollectionDataObjectIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
		ret = ret + " AND ";
		ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
		ret = ret + " AND ";
		ret = ret + emitReadableByPart(username);
		ret = ret + emitDataObjectReferenceIdReturnPart();
		return ret;
	}

	private static String emitDataObjectReferenceIdReturnPart() {
		String ret = " RETURN id(" + Constants.DATAOBJECT_IN_QUERY + "), id(" + Constants.REFERENCE_IN_QUERY + ")";
		return ret;
	}

	private static String emitCollectionDataObjectIdReturnPart() {
		String ret = " RETURN id(" + Constants.COLLECTION_IN_QUERY + "), id(" + Constants.DATAOBJECT_IN_QUERY + ")";
		return ret;
	}

	private static String emitCollectionDataObjectReferenceIdReturnPart() {
		String ret = " RETURN id(" + Constants.COLLECTION_IN_QUERY + "), id(" + Constants.DATAOBJECT_IN_QUERY + "), id("
				+ Constants.REFERENCE_IN_QUERY + ")";
		return ret;
	}

	private static String emitCollectionDataObjectDataObjectMatchPart(TraversalRules traversalRule) {
		String ret = switch (traversalRule) {
		case children -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(" + Constants.DATAOBJECT_IN_QUERY
				+ ":DataObject) ";
		case parents -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(" + Constants.DATAOBJECT_IN_QUERY
				+ ":DataObject) ";
		case successors -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject) ";
		case predecessors -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject) ";
		};
		return ret;
	}

	private static String emitCollectionDataObjectBasicReferenceMatchPart(TraversalRules traversalRule) {
		String ret = switch (traversalRule) {
		case children -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(" + Constants.DATAOBJECT_IN_QUERY
				+ ":DataObject)-[:has_reference]->(" + Constants.REFERENCE_IN_QUERY + ":BasicReference) ";
		case parents -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(" + Constants.DATAOBJECT_IN_QUERY
				+ ":DataObject)-[:has_reference]->(" + Constants.REFERENCE_IN_QUERY + ":BasicReference) ";
		case successors -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject)-[:has_reference]->(" + Constants.REFERENCE_IN_QUERY
				+ ":BasicReference) ";
		case predecessors -> "MATCH (" + Constants.COLLECTION_IN_QUERY
				+ ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-("
				+ Constants.DATAOBJECT_IN_QUERY + ":DataObject)-[:has_reference]->(" + Constants.REFERENCE_IN_QUERY
				+ ":BasicReference) ";
		};
		return ret;
	}

}
