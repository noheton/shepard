package de.dlr.shepard.common.search.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.SortingHelper;
import de.dlr.shepard.common.util.TraversalRules;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class Neo4jQueryBuilder {

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
    "createdAt",
    "updatedAt",
    "hasAnnotation"
  );

  private static final List<String> IdProperties = List.of(
    "id",
    "referencedCollectionId",
    "referencedDataObjectId",
    "fileContainerId",
    "structuredDataContainerId",
    "timeseriesContainerId"
  );

  private static String getNeo4jWithNeo4jIdString(String jsonquery, String variable) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(jsonquery, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("could not parse JSON " + e.getMessage());
    }
    return getNeo4jStringWithNeo4jId(jsonNode, variable);
  }

  private static String getNeo4jStringWithNeo4jId(JsonNode rootNode, String variable) {
    String op = "";
    try {
      op = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }
    if (opAttributes.contains(op)) {
      return primitiveClauseWithNeo4jId(rootNode, variable);
    }
    return complexClauseWithNeo4jId(rootNode, op, variable);
  }

  private static String complexClauseWithNeo4jId(JsonNode node, String operator, String variable) {
    if (!booleanOperators.contains(operator)) throw new ShepardParserException("unknown boolean operator: " + operator);
    if (operator.equals(Constants.JSON_NOT)) return notClauseWithNeo4jId(node, variable);
    else return multaryClauseWithNeo4jId(node, operator, variable);
  }

  private static String multaryClauseWithNeo4jId(JsonNode node, String operator, String variable) {
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    String firstArgument = getNeo4jStringWithNeo4jId(argumentsArray.next(), variable);
    String ret = "(" + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + " " + operator + " " + getNeo4jStringWithNeo4jId(argumentsArray.next(), variable);
    }
    ret = ret + ")";
    return ret;
  }

  private static String notClauseWithNeo4jId(JsonNode node, String variable) {
    JsonNode body = node.get(Constants.JSON_NOT);
    return "(NOT(" + getNeo4jStringWithNeo4jId(body, variable) + "))";
  }

  private static String primitiveClauseWithNeo4jId(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    property = changeAttributesDelimiter(property);
    if (notIdProperties.contains(property)) return simpleNotIdPropertyPart(node, variable);
    if (IdProperties.contains(property)) return simpleIdPropertyPart(node, variable);
    String ret = "(";
    ret = ret + "toLower(" + variable + ".`" + property + "`) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valuePart(node).toLowerCase();
    ret = ret + ")";
    return ret;
  }

  /**
   * This is a fix described in:
   * https://gitlab.com/dlr-shepard/shepard/-/issues/389
   * We use new delimiter characters for attributes ('||'), but want to support
   * the old search functionality using '.' as a delimiter.
   *
   * @param property
   * @return property string, if it contained 'attributes.', it is going to be
   * replaced by 'attributes||'
   */
  private static String changeAttributesDelimiter(String property) {
    if (property.startsWith("attributes.")) {
      return property.replaceFirst("attributes.", "attributes||");
    }
    return property;
  }

  private static String valuePart(JsonNode node) {
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

  private static String simpleNotIdPropertyPart(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    // search for creating user
    if (property.equals("createdBy") || property.equals("updatedBy")) return byPart(node, variable);
    // search for createdAt/updatedAt
    if (property.equals("createdAt") || property.equals("updatedAt")) return atPart(node, variable);
    // for SemanticAnnotationIRIs
    if (property.equals("valueIRI") || property.equals("propertyIRI")) return IRIPart(node, variable);
    // for SemanticAnnotations
    if (property.equals("hasAnnotation")) return hasAnnotationPart(node, variable);
    return null;
  }

  private static String simpleIdPropertyPart(JsonNode node, String variable) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    // for simple id
    if (property.equals("id")) return neo4jIdPart(node, variable);
    // for CollectionReferences
    if (property.equals("referencedCollectionId")) return referencedCollectionNeo4jIdPart(node, variable);
    // for DataObjectReferences
    if (property.equals("referencedDataObjectId")) return referencedDataObjectNeo4jIdPart(node, variable);
    // for FileReferences
    if (property.equals("fileContainerId")) return fileContainerIdPart(node, variable);
    // for StructuredDataReferences
    if (property.equals("structuredDataContainerId")) return structuredDataContainerIdPart(node, variable);
    // for TimeseriesReferences
    if (property.equals("timeseriesContainerId")) return timeseriesContainerIdPart(node, variable);
    return null;
  }

  private static String byPart(JsonNode node, String variable) {
    String ret = "(";
    String by =
      switch (node.get(Constants.OP_PROPERTY).textValue()) {
        case "createdBy" -> "created_by";
        case "updatedBy" -> "updated_by";
        default -> "";
      };
    ret = ret + "EXISTS {MATCH (" + variable + ") - [:" + by + "] -> (u) WHERE toLower(u.username) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE).toString().toLowerCase() + " ";
    ret = ret + "})";
    return ret;
  }

  private static String atPart(JsonNode node, String variable) {
    String ret = "(";
    String property = node.get(Constants.OP_PROPERTY).textValue();
    if (property.equals("id")) ret = ret + "id(" + variable + ") ";
    else ret = ret + variable + "." + property + " ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valuePart(node).toLowerCase();
    ret = ret + ")";
    return ret;
  }

  private static String IRIPart(JsonNode node, String variable) {
    String ret = "(";
    String iriType = node.get(Constants.OP_PROPERTY).textValue();
    ret = ret + "EXISTS {MATCH (" + variable + ") - [] -> (sem:SemanticAnnotation) WHERE (sem." + iriType + " ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE);
    ret = ret + ")})";
    return ret;
  }

  private static String hasAnnotationPart(JsonNode node, String variable) {
    String annotation = node.get(Constants.OP_PROPERTY).textValue();
    String propertyName = annotation.split(":")[0];
    String valueName = annotation.split(":")[1];
    String ret = "(";
    ret = ret + "EXISTS {MATCH (" + variable + ") - [] -> (sem:SemanticAnnotation) WHERE (sem.propertyName ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " " + propertyName + " AND ";
    ret = ret + " sem.valueName " + operatorString(node.get(Constants.OP_OPERATOR)) + " " + valueName;
    ret = ret + ")})";
    return ret;
  }

  private static String timeseriesContainerIdPart(JsonNode node, String variable) {
    return containerIdPart(node, variable, "TimeseriesContainer");
  }

  private static String structuredDataContainerIdPart(JsonNode node, String variable) {
    return containerIdPart(node, variable, "StructuredDataContainer");
  }

  private static String fileContainerIdPart(JsonNode node, String variable) {
    return containerIdPart(node, variable, "FileContainer");
  }

  private static String containerIdPart(JsonNode node, String variable, String containerType) {
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
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String referencedDataObjectNeo4jIdPart(JsonNode node, String variable) {
    String ret = "(";
    ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO + "]->(refDo:DataObject) WHERE id(refDo) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String referencedCollectionNeo4jIdPart(JsonNode node, String variable) {
    String ret = "(";
    ret =
      ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO + "]->(refCol:Collection) WHERE id(refCol) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + node.get(Constants.OP_VALUE) + " ";
    ret = ret + "})";
    return ret;
  }

  private static String neo4jIdPart(JsonNode node, String variable) {
    String ret = "(";
    ret = ret + "id(" + variable + ") ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valuePart(node);
    ret = ret + ")";
    return ret;
  }

  private static String operatorString(JsonNode node) {
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
      case Constants.JSON_REGMATCH -> "=~";
      default -> throw new ShepardParserException("unknown comparison operator " + operator);
    };
  }

  private static String collectionDataObjectMatchPartWithoutVersion() {
    String ret =
      "MATCH (" +
      Constants.COLLECTION_IN_QUERY +
      ":Collection)-[:has_dataobject]->(" +
      Constants.DATAOBJECT_IN_QUERY +
      ":DataObject)";
    return ret;
  }

  private static String collectionNeo4jIdWherePart(Long collectionId) {
    String ret = "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + collectionId + ")";
    return ret;
  }

  private static String notDeletedPart(String variable) {
    String ret = "(" + variable + ".deleted = FALSE)";
    return ret;
  }

  private static String collectionDataObjectNeo4jIdWherePart(Long collectionId, Long dataObjectId) {
    String ret =
      "(id(" +
      Constants.COLLECTION_IN_QUERY +
      ") = " +
      collectionId +
      " AND id(" +
      Constants.DATAOBJECT_IN_QUERY +
      ") = " +
      dataObjectId +
      ")";
    return ret;
  }

  private static String collectionDataObjectTraversalNeo4jIdWherePart(Long collectionId, Long dataObjectId) {
    String ret = "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + collectionId + " AND id(d) = " + dataObjectId + ")";
    return ret;
  }

  private static String basicReferenceMatchPartWithoutVersion() {
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

  public static String collectionSelectionQueryWithNeo4jId(
    String searchBodyQuery,
    String userName,
    SortingHelper sortOrder
  ) {
    String ret = "MATCH (" + Constants.COLLECTION_IN_QUERY + ":Collection)";
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.COLLECTION_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.COLLECTION_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(userName);
    if (sortOrder.hasOrderByAttribute()) {
      ret +=
        " " +
        CypherQueryHelper.getOrderByPart(
          Constants.COLLECTION_IN_QUERY,
          sortOrder.getOrderByAttribute(),
          sortOrder.getOrderDesc()
        );
    }
    return ret;
  }

  public static String containerSelectionQueryWithNeo4jId(
    String JSONQuery,
    ContainerType containerType,
    SortingHelper sortOrder,
    String userName
  ) {
    String ret = "MATCH (" + containerType.getTypeAlias() + ":" + containerType.getTypeName() + ")";
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(JSONQuery, containerType.getTypeAlias());
    ret = ret + " AND ";
    ret = ret + notDeletedPart(containerType.getTypeAlias());
    ret = ret + " AND ";
    ret = ret + CypherQueryHelper.getReadableByQuery(containerType.getTypeAlias(), userName);
    if (sortOrder.hasOrderByAttribute()) {
      ret +=
        " " +
        CypherQueryHelper.getOrderByPart(
          containerType.getTypeAlias(),
          sortOrder.getOrderByAttribute(),
          sortOrder.getOrderDesc()
        );
    }
    return ret;
  }

  public static String collectionDataObjectSelectionQueryWithNeo4jId(
    Long collectionId,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionNeo4jIdWherePart(collectionId);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + collectionDataObjectDataObjectMatchPartWithoutVersion(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectTraversalNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String dataObjectSelectionQueryWithNeo4jId(String searchBodyQuery, String username) {
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String basicReferenceSelectionQueryWithNeo4jId(String searchBodyQuery, String username) {
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String collectionBasicReferenceSelectionQueryWithNeo4jId(
    String searchBodyQuery,
    Long collectionId,
    String username
  ) {
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionNeo4jIdWherePart(collectionId);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String collectionDataObjectReferenceSelectionQueryWithNeo4jId(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  public static String collectionDataObjectBasicReferenceSelectionQueryWithNeo4jId(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    String ret = "";
    ret = ret + collectionDataObjectBasicReferenceMatchPartWithoutVersion(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectTraversalNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId());
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return ret;
  }

  private static String collectionDataObjectDataObjectMatchPartWithoutVersion(TraversalRules traversalRule) {
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

  private static String collectionDataObjectBasicReferenceMatchPartWithoutVersion(TraversalRules traversalRule) {
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

  private static String readableByPart(String username) {
    String variable = Constants.COLLECTION_IN_QUERY;
    return CypherQueryHelper.getReadableByQuery(variable, username);
  }

  public static String userSelectionQuery(String query) {
    String ret = "";
    ret = ret + userMatchPart();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(query, Constants.USER_IN_QUERY);
    return ret;
  }

  private static String userMatchPart() {
    String ret = "";
    ret = ret + "MATCH (" + Constants.USER_IN_QUERY + ":User)";
    return ret;
  }

  public static String userGroupSelectionQuery(String query) {
    String ret = "";
    ret = ret + userGroupMatchPart();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(query, Constants.USERGROUP_IN_QUERY);
    return ret;
  }

  private static String userGroupMatchPart() {
    String ret = "";
    ret = ret + "MATCH (" + Constants.USERGROUP_IN_QUERY + ":UserGroup)";
    return ret;
  }
}
