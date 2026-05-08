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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

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
    "hasAnnotation",
    "hasAnnotationIRI"
  );

  private static final List<String> IdProperties = List.of(
    "id",
    "referencedCollectionId",
    "referencedDataObjectId",
    "fileContainerId",
    "structuredDataContainerId",
    "timeseriesContainerId",
    "successorIds",
    "predecessorIds",
    "childrenIds",
    "parentIds"
  );

  /**
   * Whitelist of free-form property names accepted on the entity itself.
   * Anything not in this set must match {@link #SAFE_PROPERTY_NAME} (this
   * also covers the {@code attributes||X} family per the legacy
   * {@code attributes.X} → {@code attributes||X} rewrite). The whitelist
   * is the C5 mitigation for property-name identifier injection: Cypher
   * has no parameter binding for identifiers, so we must reject anything
   * that could escape the surrounding {@code variable.`...`} grave-accents.
   */
  private static final Set<String> KNOWN_PROPERTIES = Set.of(
    "name",
    "description",
    "createdAt",
    "updatedAt",
    "createdBy",
    "updatedBy",
    "deleted",
    "username"
  );

  /**
   * Conservative pattern for any user-supplied property name we accept
   * after the {@link #KNOWN_PROPERTIES} fallback. Matches the C5
   * recommendation in {@code aidocs/07-security-issues.md} verbatim,
   * extended with {@code |} and {@code .} so the legacy
   * {@code attributes||X} / {@code attributes.X} forms keep working.
   */
  private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_|.]*");

  /** Allow IRI-shaped strings: alphanumerics, underscore, colon. */
  private static final Pattern SAFE_IRI_FRAGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_:]*");

  private static final String INCORRECT_COLON_EXCEPTION_MESSAGE =
    "the annotation must contain exactly one occurrence of :: to divide the property and the name " +
    "but the given value is ";

  private static final String colonMatcher = "[^:]*(:[^:]+)*::([^:]+:)*[^:]*";

  /**
   * Mutable parameter accumulator threaded through the recursive Cypher
   * builders. Each call to {@link #bind(Object)} mints a fresh
   * {@code $pN} placeholder, stores the value in the parameter map, and
   * returns the placeholder string ready to be embedded in Cypher. This
   * is the C5 mechanism for binding user-controlled VALUES safely.
   */
  static final class ParamBinder {

    private final Map<String, Object> params = new HashMap<>();
    private int counter = 0;

    String bind(Object value) {
      String name = "p" + (counter++);
      params.put(name, value);
      return "$" + name;
    }

    Map<String, Object> params() {
      return params;
    }
  }

  /**
   * Validate a property identifier before splicing it into Cypher.
   * Identifiers cannot be parameter-bound in Cypher, so this is the only
   * line of defence against C5 identifier-injection.
   */
  private static String validatePropertyIdentifier(String property) {
    if (property == null) {
      throw new ShepardParserException("property name must not be null");
    }
    if (KNOWN_PROPERTIES.contains(property)) return property;
    if (SAFE_PROPERTY_NAME.matcher(property).matches()) return property;
    throw new ShepardParserException("invalid property name: " + property);
  }

  /**
   * Validate an IRI namespace identifier (annotation propertyIRI/valueIRI
   * field name) before splicing it into Cypher.
   */
  private static String validateIriIdentifier(String identifier) {
    if (identifier == null) {
      throw new ShepardParserException("IRI identifier must not be null");
    }
    if (SAFE_IRI_FRAGMENT.matcher(identifier).matches()) return identifier;
    throw new ShepardParserException("invalid IRI identifier: " + identifier);
  }

  private static String getNeo4jWithNeo4jIdString(String jsonquery, String variable, ParamBinder binder) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(jsonquery, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("could not parse JSON " + e.getMessage());
    }
    return getNeo4jStringWithNeo4jId(jsonNode, variable, binder);
  }

  private static String getNeo4jStringWithNeo4jId(JsonNode rootNode, String variable, ParamBinder binder) {
    String op = "";
    try {
      op = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }
    if (opAttributes.contains(op)) {
      return primitiveClauseWithNeo4jId(rootNode, variable, binder);
    }
    return complexClauseWithNeo4jId(rootNode, op, variable, binder);
  }

  private static String complexClauseWithNeo4jId(JsonNode node, String operator, String variable, ParamBinder binder) {
    if (!booleanOperators.contains(operator)) throw new ShepardParserException("unknown boolean operator: " + operator);
    if (operator.equals(Constants.JSON_NOT)) return notClauseWithNeo4jId(node, variable, binder);
    else return multaryClauseWithNeo4jId(node, operator, variable, binder);
  }

  private static String multaryClauseWithNeo4jId(JsonNode node, String operator, String variable, ParamBinder binder) {
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    String firstArgument = getNeo4jStringWithNeo4jId(argumentsArray.next(), variable, binder);
    String ret = "(" + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + " " + operator + " " + getNeo4jStringWithNeo4jId(argumentsArray.next(), variable, binder);
    }
    ret = ret + ")";
    return ret;
  }

  private static String notClauseWithNeo4jId(JsonNode node, String variable, ParamBinder binder) {
    JsonNode body = node.get(Constants.JSON_NOT);
    return "(NOT(" + getNeo4jStringWithNeo4jId(body, variable, binder) + "))";
  }

  private static String primitiveClauseWithNeo4jId(JsonNode node, String variable, ParamBinder binder) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    property = changeAttributesDelimiter(property);
    if (notIdProperties.contains(property)) return simpleNotIdPropertyPart(node, variable, binder);
    if (IdProperties.contains(property)) return simpleIdPropertyPart(node, variable, binder);
    String safeProperty = validatePropertyIdentifier(property);
    String ret = "(";
    ret = ret + "toLower(" + variable + ".`" + safeProperty + "`) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + lowerCasedValuePart(node, binder);
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

  /**
   * Bind the OP_VALUE as one or more Cypher parameters and return either
   * a single {@code toLower($pN)} reference or a list of such references
   * (for {@code IN} operator). The historical behaviour was to lower-case
   * the JSON-encoded Cypher fragment as a poor-man's case-fold; we
   * replicate that for every value via {@code toLower(...)} so the
   * existing search semantics survive C5 parameterisation.
   */
  private static String lowerCasedValuePart(JsonNode node, ParamBinder binder) {
    if (node.get(Constants.OP_OPERATOR).textValue().equals(Constants.JSON_IN)) {
      List<String> refs = new ArrayList<>();
      Iterator<JsonNode> setArray = node.get(Constants.OP_VALUE).elements();
      while (setArray.hasNext()) {
        refs.add("toLower(" + binder.bind(jsonValueToJava(setArray.next())) + ")");
      }
      return "[" + String.join(", ", refs) + "]";
    }
    return "toLower(" + binder.bind(jsonValueToJava(node.get(Constants.OP_VALUE))) + ")";
  }

  /**
   * Convert a {@link JsonNode} to a Java value suitable for Neo4j
   * parameter binding. Strings, numbers, and booleans are unwrapped to
   * their native Java type; everything else falls back to the JSON text
   * representation.
   */
  private static Object jsonValueToJava(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (node.isTextual()) return node.textValue();
    if (node.isInt()) return node.intValue();
    if (node.isLong()) return node.longValue();
    if (node.isDouble() || node.isFloat()) return node.doubleValue();
    if (node.isBoolean()) return node.booleanValue();
    if (node.isNumber()) return node.numberValue();
    return node.toString();
  }

  /**
   * Bind OP_VALUE as a Cypher parameter (single value or list literal of
   * parameters) without the case-folding wrapper. Used for id() / id-like
   * comparisons and for IRI-string equality where lower-casing would
   * change semantics.
   */
  private static String rawValuePart(JsonNode node, ParamBinder binder) {
    if (node.get(Constants.OP_OPERATOR).textValue().equals(Constants.JSON_IN)) {
      List<String> refs = new ArrayList<>();
      Iterator<JsonNode> setArray = node.get(Constants.OP_VALUE).elements();
      while (setArray.hasNext()) {
        refs.add(binder.bind(jsonValueToJava(setArray.next())));
      }
      return "[" + String.join(", ", refs) + "]";
    }
    return binder.bind(jsonValueToJava(node.get(Constants.OP_VALUE)));
  }

  private static String simpleNotIdPropertyPart(JsonNode node, String variable, ParamBinder binder) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    // search for creating user
    if (property.equals("createdBy") || property.equals("updatedBy")) return byPart(node, variable, binder);
    // search for createdAt/updatedAt
    if (property.equals("createdAt") || property.equals("updatedAt")) return atPart(node, variable, binder);
    // for SemanticAnnotationIRIs
    if (property.equals("valueIRI") || property.equals("propertyIRI")) return iRIPart(node, variable, binder);
    // for SemanticAnnotations
    if (property.equals("hasAnnotation")) return hasAnnotationPart(node, variable, binder);
    // for SemanticAnnotations
    if (property.equals("hasAnnotationIRI")) return hasAnnotationIRIPart(node, variable, binder);
    return null;
  }

  private static String hasAnnotationIRIPart(JsonNode node, String variable, ParamBinder binder) {
    //REMARK:
    //the split function in JAVA behaves sometimes in an inconsistent way:
    //"::".split("::") = String[0] {  }
    //"x::y".split("::") = String[2] { "x", "y" }
    //"::y".split("::") = String[2] { "", "y" }
    //"x::".split("::") = String[1] { "x" }
    //to extract the empty string from the annotation pair correctly some additional work is needed
    String annotation = node.get(Constants.OP_VALUE).textValue();
    if (!annotation.matches(colonMatcher)) throw new ShepardParserException(
      INCORRECT_COLON_EXCEPTION_MESSAGE + annotation
    );
    String[] propertyValuePair = annotation.split("::");
    String propertyIRI = null;
    String valueIRI = null;
    //case ::
    if (propertyValuePair.length == 0) {
      propertyIRI = "";
      valueIRI = "";
    }
    //case x::
    if (propertyValuePair.length == 1) {
      propertyIRI = propertyValuePair[0];
      valueIRI = "";
    }
    //case x::y (where x may be the empty string)
    if (propertyValuePair.length == 2) {
      propertyIRI = propertyValuePair[0];
      valueIRI = propertyValuePair[1];
    }
    String propIriRef = binder.bind(propertyIRI);
    String valIriRef = binder.bind(valueIRI);
    String op = operatorString(node.get(Constants.OP_OPERATOR));
    String ret = "(";
    ret =
      ret + "EXISTS {MATCH (" + variable + ") - [:has_annotation] -> (sem:SemanticAnnotation) WHERE (sem.propertyIRI ";
    ret = ret + op + " " + propIriRef + " AND ";
    ret = ret + " sem.valueIRI " + op + " " + valIriRef;
    ret = ret + ")})";
    return ret;
  }

  private static String hasAnnotationPart(JsonNode node, String variable, ParamBinder binder) {
    //REMARK:
    //the split function in JAVA behaves sometimes in an inconsistent way:
    //"::".split("::") = String[0] {  }
    //"x::y".split("::") = String[2] { "x", "y" }
    //"::y".split("::") = String[2] { "", "y" }
    //"x::".split("::") = String[1] { "x" }
    //to extract the empty string from the annotation pair correctly some additional work is needed
    String annotation = node.get(Constants.OP_VALUE).textValue();
    if (!annotation.matches(colonMatcher)) throw new ShepardParserException(
      INCORRECT_COLON_EXCEPTION_MESSAGE + annotation
    );
    String[] propertyValuePair = annotation.split("::");
    String propertyName = null;
    String valueName = null;
    //case ::
    if (propertyValuePair.length == 0) {
      propertyName = "";
      valueName = "";
    }
    //case x::
    if (propertyValuePair.length == 1) {
      propertyName = propertyValuePair[0];
      valueName = "";
    }
    //case x::y (where x may be the empty string)
    if (propertyValuePair.length == 2) {
      propertyName = propertyValuePair[0];
      valueName = propertyValuePair[1];
    }
    String propNameRef = binder.bind(propertyName);
    String valNameRef = binder.bind(valueName);
    String op = operatorString(node.get(Constants.OP_OPERATOR));
    String ret = "(";
    ret =
      ret + "EXISTS {MATCH (" + variable + ") - [:has_annotation] -> (sem:SemanticAnnotation) WHERE (sem.propertyName ";
    ret = ret + op + " " + propNameRef + " AND ";
    ret = ret + " sem.valueName " + op + " " + valNameRef;
    ret = ret + ")})";
    return ret;
  }

  private static String simpleIdPropertyPart(JsonNode node, String variable, ParamBinder binder) {
    String property = node.get(Constants.OP_PROPERTY).textValue();
    // for simple id
    if (property.equals("id")) return neo4jIdPart(node, variable, binder);
    // for CollectionReferences
    if (property.equals("referencedCollectionId")) return referencedCollectionNeo4jIdPart(node, variable, binder);
    // for DataObjectReferences
    if (property.equals("referencedDataObjectId")) return referencedDataObjectNeo4jIdPart(node, variable, binder);
    // for FileReferences
    if (property.equals("fileContainerId")) return fileContainerIdPart(node, variable, binder);
    // for StructuredDataReferences
    if (property.equals("structuredDataContainerId")) return structuredDataContainerIdPart(node, variable, binder);
    // for TimeseriesReferences
    if (property.equals("timeseriesContainerId")) return timeseriesContainerIdPart(node, variable, binder);
    // for neighborhoodIds
    if (
      property.equals("successorIds") ||
      property.equals("predecessorIds") ||
      property.equals("childrenIds") ||
      property.equals("parentIds")
    ) return neighborhoodIdsPart(node, variable, binder);
    return null;
  }

  private static String byPart(JsonNode node, String variable, ParamBinder binder) {
    String ret = "(";
    String by =
      switch (node.get(Constants.OP_PROPERTY).textValue()) {
        case "createdBy" -> "created_by";
        case "updatedBy" -> "updated_by";
        default -> "";
      };
    String valueRef = binder.bind(jsonValueToJava(node.get(Constants.OP_VALUE)));
    ret = ret + "EXISTS {MATCH (" + variable + ") - [:" + by + "] -> (u) WHERE toLower(u.username) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + "toLower(" + valueRef + ") ";
    ret = ret + "})";
    return ret;
  }

  private static String neighborhoodIdsPart(JsonNode node, String variable, ParamBinder binder) {
    String operatorString = node.get(Constants.OP_OPERATOR).textValue();
    String neighborhoodProperty = node.get(Constants.OP_PROPERTY).textValue();
    String neighborhoodNeo4j =
      switch (neighborhoodProperty) {
        case "successorIds" -> "-[:has_successor]->";
        case "predecessorIds" -> "<-[:has_successor]-";
        case "childrenIds" -> "-[:has_child]->";
        case "parentIds" -> "<-[:has_child]-";
        default -> "";
      };
    if (operatorString.equals(Constants.JSON_CONTAINS)) return neighborhoodIdsContainsPart(
      node,
      variable,
      neighborhoodNeo4j,
      binder
    );
    if (operatorString.equals(Constants.JSON_IS_CONTAINED_IN)) return neighborhoodIdsIsContainedInPart(
      node,
      variable,
      neighborhoodNeo4j,
      binder
    );
    if (operatorString.equals(Constants.JSON_EQ)) return neighborhoodIdsEqualsPart(
      node,
      variable,
      neighborhoodNeo4j,
      binder
    );
    throw new ShepardParserException("illegal comparison operator " + operatorString);
  }

  private static String neighborhoodIdsEqualsPart(
    JsonNode node,
    String variable,
    String neighborhoodNeo4j,
    ParamBinder binder
  ) {
    return (
      "((" +
      neighborhoodIdsIsContainedInPart(node, variable, neighborhoodNeo4j, binder) +
      ") AND (" +
      neighborhoodIdsContainsPart(node, variable, neighborhoodNeo4j, binder) +
      "))"
    );
  }

  private static String neighborhoodIdsIsContainedInPart(
    JsonNode node,
    String variable,
    String neighborhoodNeo4j,
    ParamBinder binder
  ) {
    String ret = "(";
    ArrayList<Long> successorIds = new ArrayList<Long>();
    for (JsonNode longNode : node.get(Constants.OP_VALUE)) {
      successorIds.add(longNode.longValue());
    }
    if (successorIds.size() == 0) return "(NOT EXISTS{MATCH (" + variable + ")" + neighborhoodNeo4j + "(neighborObj)})";
    String idsRef = binder.bind(new ArrayList<>(successorIds));
    ret =
      ret +
      "NOT EXISTS{MATCH (" +
      variable +
      ")" +
      neighborhoodNeo4j +
      "(neighborObj) WHERE (NOT id(neighborObj) IN " +
      idsRef +
      ")})";
    return ret;
  }

  private static String neighborhoodIdsContainsPart(
    JsonNode node,
    String variable,
    String neighborhoodNeo4j,
    ParamBinder binder
  ) {
    String ret = "(";
    ArrayList<Long> successorIds = new ArrayList<Long>();
    for (JsonNode longNode : node.get(Constants.OP_VALUE)) {
      successorIds.add(longNode.longValue());
    }
    if (successorIds.size() == 0) return "(1=1)";
    for (int i = 0; i < successorIds.size() - 1; i++) {
      String idRef = binder.bind(successorIds.get(i));
      ret =
        ret +
        "(EXISTS {MATCH (" +
        variable +
        ")" +
        neighborhoodNeo4j +
        "(neighborObj) WHERE id(neighborObj)=" +
        idRef +
        "}) AND ";
    }
    String lastIdRef = binder.bind(successorIds.getLast());
    ret =
      ret +
      "(EXISTS {MATCH (" +
      variable +
      ")" +
      neighborhoodNeo4j +
      "(neighborObj) WHERE id(neighborObj)=" +
      lastIdRef +
      "}))";
    return ret;
  }

  private static String atPart(JsonNode node, String variable, ParamBinder binder) {
    String ret = "(";
    String property = node.get(Constants.OP_PROPERTY).textValue();
    if (property.equals("id")) ret = ret + "id(" + variable + ") ";
    else {
      // Defence in depth: this branch is only reached for createdAt/updatedAt
      // per the simpleNotIdPropertyPart routing, but we still validate the
      // identifier so accidental future routing changes can never inject.
      String safeProperty = validatePropertyIdentifier(property);
      ret = ret + variable + "." + safeProperty + " ";
    }
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + lowerCasedValuePart(node, binder);
    ret = ret + ")";
    return ret;
  }

  private static String iRIPart(JsonNode node, String variable, ParamBinder binder) {
    String ret = "(";
    String iriType = validateIriIdentifier(node.get(Constants.OP_PROPERTY).textValue());
    String valueRef = rawValuePart(node, binder);
    ret = ret + "EXISTS {MATCH (" + variable + ") - [] -> (sem:SemanticAnnotation) WHERE (sem." + iriType + " ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valueRef;
    ret = ret + ")})";
    return ret;
  }

  private static String timeseriesContainerIdPart(JsonNode node, String variable, ParamBinder binder) {
    return containerIdPart(node, variable, "TimeseriesContainer", binder);
  }

  private static String structuredDataContainerIdPart(JsonNode node, String variable, ParamBinder binder) {
    return containerIdPart(node, variable, "StructuredDataContainer", binder);
  }

  private static String fileContainerIdPart(JsonNode node, String variable, ParamBinder binder) {
    return containerIdPart(node, variable, "FileContainer", binder);
  }

  private static String containerIdPart(JsonNode node, String variable, String containerType, ParamBinder binder) {
    String valueRef = rawValuePart(node, binder);
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
    ret = ret + valueRef + " ";
    ret = ret + "})";
    return ret;
  }

  private static String referencedDataObjectNeo4jIdPart(JsonNode node, String variable, ParamBinder binder) {
    String valueRef = rawValuePart(node, binder);
    String ret = "(";
    ret = ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO + "]->(refDo:DataObject) WHERE id(refDo) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valueRef + " ";
    ret = ret + "})";
    return ret;
  }

  private static String referencedCollectionNeo4jIdPart(JsonNode node, String variable, ParamBinder binder) {
    String valueRef = rawValuePart(node, binder);
    String ret = "(";
    ret =
      ret + "EXISTS {MATCH (" + variable + ")-[:" + Constants.POINTS_TO + "]->(refCol:Collection) WHERE id(refCol) ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + valueRef + " ";
    ret = ret + "})";
    return ret;
  }

  private static String neo4jIdPart(JsonNode node, String variable, ParamBinder binder) {
    String ret = "(";
    ret = ret + "id(" + variable + ") ";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + " ";
    ret = ret + rawValuePart(node, binder);
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

  private static String collectionNeo4jIdWherePart(Long collectionId, ParamBinder binder) {
    String ref = binder.bind(collectionId);
    return "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + ref + ")";
  }

  private static String notDeletedPart(String variable) {
    String ret = "(" + variable + ".deleted = FALSE)";
    return ret;
  }

  private static String collectionDataObjectNeo4jIdWherePart(Long collectionId, Long dataObjectId, ParamBinder binder) {
    String colRef = binder.bind(collectionId);
    String doRef = binder.bind(dataObjectId);
    return (
      "(id(" +
      Constants.COLLECTION_IN_QUERY +
      ") = " +
      colRef +
      " AND id(" +
      Constants.DATAOBJECT_IN_QUERY +
      ") = " +
      doRef +
      ")"
    );
  }

  private static String collectionDataObjectTraversalNeo4jIdWherePart(
    Long collectionId,
    Long dataObjectId,
    ParamBinder binder
  ) {
    String colRef = binder.bind(collectionId);
    String doRef = binder.bind(dataObjectId);
    return "(id(" + Constants.COLLECTION_IN_QUERY + ") = " + colRef + " AND id(d) = " + doRef + ")";
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

  public static Neo4jQuery collectionSelectionQueryWithNeo4jId(
    String searchBodyQuery,
    String userName,
    SortingHelper sortOrder
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "MATCH (" + Constants.COLLECTION_IN_QUERY + ":Collection)";
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.COLLECTION_IN_QUERY, binder);
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
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery containerSelectionQueryWithNeo4jId(
    String JSONQuery,
    ContainerType containerType,
    SortingHelper sortOrder,
    String userName
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "MATCH (" + containerType.getTypeAlias() + ":" + containerType.getTypeName() + ")";
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(JSONQuery, containerType.getTypeAlias(), binder);
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
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionDataObjectSelectionQueryWithNeo4jId(
    Long collectionId,
    String searchBodyQuery,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionNeo4jIdWherePart(collectionId, binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + collectionDataObjectDataObjectMatchPartWithoutVersion(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectTraversalNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId(), binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId(), binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery dataObjectSelectionQueryWithNeo4jId(String searchBodyQuery, String username) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + collectionDataObjectMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.DATAOBJECT_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.DATAOBJECT_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery basicReferenceSelectionQueryWithNeo4jId(String searchBodyQuery, String username) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionBasicReferenceSelectionQueryWithNeo4jId(
    String searchBodyQuery,
    Long collectionId,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionNeo4jIdWherePart(collectionId, binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionDataObjectReferenceSelectionQueryWithNeo4jId(
    SearchScope scope,
    String searchBodyQuery,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + basicReferenceMatchPartWithoutVersion();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId(), binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
  }

  public static Neo4jQuery collectionDataObjectBasicReferenceSelectionQueryWithNeo4jId(
    SearchScope scope,
    TraversalRules traversalRule,
    String searchBodyQuery,
    String username
  ) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + collectionDataObjectBasicReferenceMatchPartWithoutVersion(traversalRule);
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(searchBodyQuery, Constants.REFERENCE_IN_QUERY, binder);
    ret = ret + " AND ";
    ret = ret + collectionDataObjectTraversalNeo4jIdWherePart(scope.getCollectionId(), scope.getDataObjectId(), binder);
    ret = ret + " AND ";
    ret = ret + notDeletedPart(Constants.REFERENCE_IN_QUERY);
    ret = ret + " AND ";
    ret = ret + readableByPart(username);
    return new Neo4jQuery(ret, binder.params());
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

  public static Neo4jQuery userSelectionQuery(String query) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + userMatchPart();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(query, Constants.USER_IN_QUERY, binder);
    return new Neo4jQuery(ret, binder.params());
  }

  private static String userMatchPart() {
    String ret = "";
    ret = ret + "MATCH (" + Constants.USER_IN_QUERY + ":User)";
    return ret;
  }

  public static Neo4jQuery userGroupSelectionQuery(String query) {
    ParamBinder binder = new ParamBinder();
    String ret = "";
    ret = ret + userGroupMatchPart();
    ret = ret + " WHERE ";
    ret = ret + getNeo4jWithNeo4jIdString(query, Constants.USERGROUP_IN_QUERY, binder);
    return new Neo4jQuery(ret, binder.params());
  }

  private static String userGroupMatchPart() {
    String ret = "";
    ret = ret + "MATCH (" + Constants.USERGROUP_IN_QUERY + ":UserGroup)";
    return ret;
  }
}
