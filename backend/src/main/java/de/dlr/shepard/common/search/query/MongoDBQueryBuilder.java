package de.dlr.shepard.common.search.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.util.Constants;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class MongoDBQueryBuilder {

  private static final List<String> booleanOperators = List.of(
    Constants.JSON_AND,
    Constants.JSON_OR,
    Constants.JSON_NOT
  );
  private static final List<String> opAttributes = List.of(
    Constants.OP_PROPERTY,
    Constants.OP_VALUE,
    Constants.OP_OPERATOR
  );

  private MongoDBQueryBuilder() {}

  public static String getMongoDBQueryString(String query) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(query, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("error while reading JSON\n" + e.getMessage());
    }
    return getMongoDBQueryString(jsonNode);
  }

  private static String getMongoDBQueryString(JsonNode rootNode) {
    String operator = "";
    try {
      operator = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }

    if (opAttributes.contains(operator)) {
      return primitiveClause(rootNode);
    } else if (booleanOperators.contains(operator)) {
      return complexClause(rootNode, operator);
    } else {
      throw new ShepardParserException("unknown operator: " + operator);
    }
  }

  private static String operatorString(JsonNode node) {
    String operator = node.textValue();
    return switch (operator) {
      case Constants.JSON_GT -> "$gt";
      case Constants.JSON_LT -> "$lt";
      case Constants.JSON_GE -> "$gte";
      case Constants.JSON_LE -> "$lte";
      case Constants.JSON_EQ -> "$eq";
      case Constants.JSON_IN -> "$in";
      case Constants.JSON_NE -> "$ne";
      default -> throw new ShepardParserException("unknown comparison operator " + operator);
    };
  }

  private static String complexClause(JsonNode node, String operator) {
    if (operator.equals(Constants.JSON_NOT)) {
      return negatedClause(node.get(Constants.JSON_NOT));
    } else {
      return multaryClause(node, operator);
    }
  }

  private static String negatedClause(JsonNode node) {
    String operator = node.fieldNames().next();
    if (opAttributes.contains(operator)) {
      return negatedPrimitiveClause(node);
    } else if (booleanOperators.contains(operator)) {
      return negatedComplexClause(node, operator);
    } else {
      throw new ShepardParserException("unknown operator: " + operator);
    }
  }

  private static String negatedComplexClause(JsonNode node, String operator) {
    if (operator.equals(Constants.JSON_NOT)) return getMongoDBQueryString(node.get(Constants.JSON_NOT));
    else return negatedMultaryClause(node, operator);
  }

  private static String negatedPrimitiveClause(JsonNode node) {
    String ret = "";
    ret = ret + node.get(Constants.OP_PROPERTY).textValue() + ": {";
    ret = ret + "$not: {";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + ": ";
    ret = ret + node.get(Constants.OP_VALUE) + "}}";
    return ret;
  }

  private static String primitiveClause(JsonNode node) {
    String ret = "";
    String property = node.get(Constants.OP_PROPERTY).textValue();
    ret = ret + property + ": {";
    ret = ret + operatorString(node.get(Constants.OP_OPERATOR)) + ": ";
    ret = ret + node.get(Constants.OP_VALUE);
    ret = ret + "}";
    return ret;
  }

  private static String multaryClause(JsonNode node, String operator) {
    String ret = "";
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    ret = ret + booleanOperator(operator) + " [{";
    String firstArgument = getMongoDBQueryString(argumentsArray.next());
    ret = ret + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + "}, {" + getMongoDBQueryString(argumentsArray.next());
    }
    ret = ret + "}]";
    return ret;
  }

  private static String negatedMultaryClause(JsonNode node, String operator) {
    String ret = "";
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    ret = ret + negatedBooleanOperator(operator) + " [{";
    String firstArgument = negatedClause(argumentsArray.next());
    ret = ret + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + "}, {" + negatedClause(argumentsArray.next());
    }
    ret = ret + "}]";
    return ret;
  }

  private static String booleanOperator(String operator) {
    return switch (operator) {
      case Constants.JSON_AND -> "$and:";
      case Constants.JSON_OR -> "$or:";
      default -> throw new ShepardParserException("unknown operator: " + operator);
    };
  }

  private static String negatedBooleanOperator(String operator) {
    return switch (operator) {
      case Constants.JSON_AND -> "$or:";
      case Constants.JSON_OR -> "$and:";
      default -> throw new ShepardParserException("unknown operator: " + operator);
    };
  }
}
