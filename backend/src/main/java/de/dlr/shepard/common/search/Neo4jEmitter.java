package de.dlr.shepard.common.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.unified.SearchScope;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class Neo4jEmitter {

  private static final List<String> booleanOperators = List.of(
    Constants.JSON_AND,
    Constants.JSON_OR,
    Constants.JSON_NOT,
    Constants.JSON_XOR
  );
  private static final List<String> opAttributes = List.of(
    Constants.OP_PROPERTY,
    Constants.OP_VALUE,
    Constants.OP_OPERATOR
  );
  private static final List<String> notIdProperties = List.of(
    "createdBy",
    "updatedBy",
    "valueIRI",
    "propertyIRI",
    "referencedCollectionId",
    "referencedDataObjectId",
    "fileContainerId",
    "structuredDataContainerId",
    "timeseriesContainerId"
  );

  private static String emitNeo4j(String jsonquery, String variable) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(jsonquery, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("could not parse JSON\n" + e.getMessage());
    }
    return emitNeo4j(jsonNode, variable);
  }

  private static String emitNeo4jWithShepardId(String jsonquery, String variable) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(jsonquery, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("could not parse JSON\n" + e.getMessage());
    }
    return emitNeo4jWithShepardId(jsonNode, variable);
  }

  private static String emitNeo4j(JsonNode rootNode, String variable) {
    String op = "";
    try {
      op = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }
    if (opAttributes.contains(op)) {
      return emitPrimitiveClause(rootNode, variable);
    }
    return emitComplexClause(rootNode, op, variable);
  }

  private static String emitNeo4jWithShepardId(JsonNode rootNode, String variable) {
    String op = "";
    try {
      op = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }
    if (opAttributes.contains(op)) {
      return emitPrimitiveClauseWithShepardId(rootNode, variable);
    }
    return emitComplexClauseWithShepardId(rootNode, op, variable);
  }

  private static String emitComplexClause(JsonNode node, String operator, String variable) {
    if (!booleanOperators.contains(operator)) throw new ShepardParserException("unknown boolean operator: " + operator);
    if (operator.equals(Constants.JSON_NOT)) return emitNotClause(node, variable);
    else return emitMultaryClause(node, operator, variable);
  }

  private static String emitComplexClauseWithShepardId(JsonNode node, String operator, String variable) {
    if (!booleanOperators.contains(operator)) throw new ShepardParserException("unknown boolean operator: " + operator);
    if (operator.equals(Constants.JSON_NOT)) return emitNotClauseWithShepardId(node, variable);
    else return emitMultaryClauseWithShepardId(node, operator, variable);
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

  private static String emitMultaryClauseWithShepardId(JsonNode node, String operator, String variable) {
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    String firstArgument = emitNeo4jWithShepardId(argumentsArray.next(), variable);
    String ret = "(" + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + " " + operator + " " + emitNeo4jWithShepardId(argumentsArray.next(), variable);
    }
    ret = ret + ")";
    return ret;
  }

  private static String emitNotClause(JsonNode node, String variable) {
    JsonNode body = node.get(Constants.JSON_NOT);
    return "(NOT(" + emitNeo4j(body, variable) + "))";
  }

  private static String emitNotClauseWithShepardId(JsonNode node, String variable) {
    JsonNode body = node.get(Constants.JSON_NOT);
    return "(NOT(" + emitNeo4jWithShepardId(body, variable) + "))";
  }

  private static String emitPrimitiveClause(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    property = changeAttributesDelimiter(property);

    if (notIdProperties.contains(property)) return emitSimplePropertyPart(node, variable);
    String ret = "(";
    if (property.equals("id")) ret = ret + "id(" + variable + ")";
    else ret = ret + variable + ".`" + property + "` ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + emitValuePart(node);
    ret = ret + ")";
    return ret;
  }

  private static String emitPrimitiveClauseWithShepardId(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    property = changeAttributesDelimiter(property);

    if (notIdProperties.contains(property)) return emitSimplePropertyPart(node, variable);
    String ret = "(";
    if (property.equals("id")) ret = ret + variable + "." + Constants.SHEPARD_ID + " ";
    else ret = ret + variable + ".`" + property + "` ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + emitValuePart(node);
    ret = ret + ")";
    return ret;
  }

  /**
   * This is a fix described in: https://gitlab.com/dlr-shepard/shepard/-/issues/389
   * We use new delimiter characters for attributes ('||'), but want to support the old search functionality using '.' as a delimiter.
   * @param property
   * @return property string, if it contained 'attributes.', it is going to be replaced by 'attributes||'
   */
  private static String changeAttributesDelimiter(String property) {
    if (property.startsWith("attributes.")) {
      return property.replaceFirst("attributes.", "attributes||");
    }
    return property;
  }

  private static String emitValuePart(JsonNode node) {
    String ret = "";
    if (node.get(Constants.OP_OPERATOR).textValue().equals(Constants.JSON_IN)) {
      ret = ret + "[";
      Iterator<JsonNode> setArray = node.get(Constants.OP_VALUE).elements();
      if (setArray.hasNext()) {
        ret = ret + setArray.next();
        while (setArray.hasNext()) ret = ret + ", " + setArray.next();
        ret = ret + "]";
      } else {
        ret = ret + "]";
      }
    } else ret = ret + node.get(Constants.OP_VALUE);
    return ret;
  }

  private static String emitSimplePropertyPart(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    // search for creating user
    if (property.equals("createdBy") || property.equals("updatedBy")) return emitByPart(node, variable);
    // for SemanticAnnotationIRIs
    if (property.equals("valueIRI") || property.equals("propertyIRI")) return emitIRIPart(node, variable);
    // for CollectionReferences
    if (property.equals("referencedCollectionId")) return emitReferencedCollectionIdPart(node, variable);
    // for DataObjectReferences
    if (property.equals("referencedDataObjectId")) return emitReferencedDataObjectIdPart(node, variable);
    // for FileReferences
    if (property.equals("fileContainerId")) return emitFileContainerIdPart(node, variable);
    // for StructuredDataReferences
    if (property.equals("structuredDataContainerId")) return emitStructuredDataContainerIdPart(node, variable);
    // for TimeseriesReferences
    if (property.equals("timeseriesContainerId")) return emitTimeseriesContainerIdPart(node, variable);
    return null;
  }

  private static String emitByPart(JsonNode node, String variable) {
    String ret = "(";
    String by =
      switch (node.get(Constants.OP_PROPERTY).textValue()) {
        case "createdBy" -> "created_by";
        case "updatedBy" -> "updated_by";
        default -> "";
      };
    ret = ret + "EXISTS {MATCH (" + variable + ") - [:" + by + "] -> (u) WHERE u.username ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String emitIRIPart(JsonNode node, String variable) {
    String ret = "(";
    String iriType = node.get(Constants.OP_PROPERTY).textValue();
    ret = ret + "EXISTS {MATCH (" + variable + ") - [] -> (sem:SemanticAnnotation) WHERE (sem." + iriType + " ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE);
    ret = ret + ")})";
    return ret;
  }

  private static String emitTimeseriesContainerIdPart(JsonNode node, String variable) {
    return emitContainerIdPart(node, variable, "TimeseriesContainer");
  }

  private static String emitStructuredDataContainerIdPart(JsonNode node, String variable) {
    return emitContainerIdPart(node, variable, "StructuredDataContainer");
  }

  private static String emitFileContainerIdPart(JsonNode node, String variable) {
    return emitContainerIdPart(node, variable, "FileContainer");
  }

  private static String emitContainerIdPart(JsonNode node, String variable, String containerType) {
    String ret = "(";
    ret =
      ret +
      "EXISTS {MATCH (" +
      variable +
      ")-[:" +
      Constants.IS_IN_CONTAINER +
      "]->(refCon:" +
      containerType +
      ") WHERE id(refCon) ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String emitReferencedDataObjectIdPart(JsonNode node, String variable) {
    String ret = "(";
    ret =
      ret +
      "EXISTS {MATCH (" +
      variable +
      ")-[:" +
      Constants.POINTS_TO +
      "]->(refDo:DataObject) WHERE refDo." +
      Constants.SHEPARD_ID +
      " ";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String emitReferencedCollectionIdPart(JsonNode node, String variable) {
    String ret = "(";
    ret =
      ret +
      "EXISTS {MATCH (" +
      variable +
      ")-[:" +
      Constants.POINTS_TO +
      "]->(refCol:Collection) WHERE refCol." +
      Constants.SHEPARD_ID +
      " ";
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

  private static String emitCollectionDataObjectMatchPart() {
    String ret =
      "MATCH (" +
      Constants.COLLECTION_IN_QUERY +
      ":Collection)-[:has_dataobject]->(" +
      Constants.DATAOBJECT_IN_QUERY +
      ":DataObject)";
    return ret;
  }

  private static String emitCollectionIdWherePart(Long collectionId) {
    String ret = "(" + Constants.COLLECTION_IN_QUERY + "." + Constants.SHEPARD_ID + " = " + collectionId + ")";
    return ret;
  }

  private static String emitNotDeletedPart(String variable) {
    String ret = "(" + variable + ".deleted = FALSE)";
    return ret;
  }

  private static String emitCollectionDataObjectIdWherePart(Long collectionId, Long dataObjectId) {
    String ret =
      "(" +
      Constants.COLLECTION_IN_QUERY +
      "." +
      Constants.SHEPARD_ID +
      " = " +
      collectionId +
      " AND " +
      Constants.DATAOBJECT_IN_QUERY +
      "." +
      Constants.SHEPARD_ID +
      " = " +
      dataObjectId +
      ")";
    return ret;
  }

  private static String emitCollectionDataObjectTraversalIdWherePart(Long collectionId, Long dataObjectId) {
    String ret =
      "(" +
      Constants.COLLECTION_IN_QUERY +
      "." +
      Constants.SHEPARD_ID +
      " = " +
      collectionId +
      " AND d." +
      Constants.SHEPARD_ID +
      " = " +
      dataObjectId +
      ")";
    return ret;
  }

  private static String emitReferenceMatchPart() {
    String ret =
      "MATCH (" +
      Constants.COLLECTION_IN_QUERY +
      ":Collection)-[:has_dataobject]->(" +
      Constants.DATAOBJECT_IN_QUERY +
      ":DataObject)-[:has_reference]->(" +
      Constants.REFERENCE_IN_QUERY +
      ":BasicReference)";
    return ret;
  }

  public static String emitCollectionSelectionQuery(String searchBodyQuery, String userName) {
    String ret = "";
    ret = ret + emitCollectionMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.COLLECTION_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.COLLECTION_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(userName);
    return ret;
  }

  public static String emitContainerSelectionQuery(
    String JSONQuery,
    ContainerType containerType,
    QueryParamHelper params,
    String userName
  ) {
    String ret = "MATCH (" + containerType.getTypeAlias() + ":" + containerType.getTypeName() + ")";
    ret = ret + " WHERE ";
    ret = ret + emitNeo4j(JSONQuery, containerType.getTypeAlias());
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(containerType.getTypeAlias());
    ret = ret + " AND ";
    ret = ret + CypherQueryHelper.getReadableByQuery(containerType.getTypeAlias(), userName);
    if (params.hasOrderByAttribute()) {
      ret +=
        " " +
        CypherQueryHelper.getOrderByPart(
          containerType.getTypeAlias(),
          params.getOrderByAttribute(),
          params.getOrderDesc()
        );
    }

    return ret;
  }

  public static String emitCollectionDataObjectSelectionQuery(
    Long collectionId,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + emitCollectionDataObjectMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionIdWherePart(collectionId);
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitCollectionDataObjectDataObjectSelectionQuery(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + emitCollectionDataObjectDataObjectMatchPart(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionDataObjectTraversalIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitCollectionDataObjectDataObjectSelectionQuery(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + emitCollectionDataObjectMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionDataObjectIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitDataObjectSelectionQuery(String searchBodyQuery, String username) {
    String ret = "";
    ret = ret + emitCollectionDataObjectMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitBasicReferenceSelectionQuery(String searchBodyQuery, String username) {
    String ret = "";
    ret = ret + emitReferenceMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitCollectionBasicReferenceSelectionQuery(
    String searchBodyQuery,
    Long collectionId,
    String username
  ) {
    String ret = "";
    ret = ret + emitReferenceMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionIdWherePart(collectionId);
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitCollectionDataObjectReferenceSelectionQuery(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + emitReferenceMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionDataObjectIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  public static String emitCollectionDataObjectBasicReferenceSelectionQuery(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + emitCollectionDataObjectBasicReferenceMatchPart(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + emitNeo4jWithShepardId(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitCollectionDataObjectTraversalIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + emitNotDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + emitReadableByPart(username);
    return ret;
  }

  private static String emitCollectionDataObjectDataObjectMatchPart(TraversalRules traversalRule) {
    String ret =
      switch (traversalRule) {
        case children -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)";
        case parents -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)";
        case successors -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)";
        case predecessors -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)";
        default -> "";
      };
    return ret;
  }

  private static String emitCollectionDataObjectBasicReferenceMatchPart(TraversalRules traversalRule) {
    String ret =
      switch (traversalRule) {
        case children -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_child*0..]->(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)-[:has_reference]->(" +
        Constants.REFERENCE_IN_QUERY +
        ":BasicReference)";
        case parents -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_child*0..]-(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)-[:has_reference]->(" +
        Constants.REFERENCE_IN_QUERY +
        ":BasicReference)";
        case successors -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)-[:has_successor*0..]->(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)-[:has_reference]->(" +
        Constants.REFERENCE_IN_QUERY +
        ":BasicReference)";
        case predecessors -> "MATCH (" +
        Constants.COLLECTION_IN_QUERY +
        ":Collection)-[:has_dataobject]->(d:DataObject)<-[:has_successor*0..]-(" +
        Constants.DATAOBJECT_IN_QUERY +
        ":DataObject)-[:has_reference]->(" +
        Constants.REFERENCE_IN_QUERY +
        ":BasicReference)";
        default -> "";
      };
    return ret;
  }

  private static String emitReadableByPart(String username) {
    String variable = Constants.COLLECTION_IN_QUERY;
    return CypherQueryHelper.getReadableByQuery(variable, username);
  }

  public static String emitUserSelectionQuery(String query) {
    String ret = "";
    ret = ret + emitUserMatchPart();
    ret = ret + " WHERE ";
    ret = ret + emitNeo4j(query, Constants.USER_IN_QUERY);
    return ret;
  }

  private static String emitUserMatchPart() {
    String ret = "";
    ret = ret + "MATCH (" + Constants.USER_IN_QUERY + ":User)";
    return ret;
  }
}
