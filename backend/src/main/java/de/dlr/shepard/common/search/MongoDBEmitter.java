package de.dlr.shepard.common.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ShepardParserException;
import de.dlr.shepard.common.util.Constants;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class MongoDBEmitter {

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

  private MongoDBEmitter() {}

  public static String emitMongoDB(String query) {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readValue(query, JsonNode.class);
    } catch (JsonProcessingException e) {
      throw new ShepardParserException("error while reading JSON\n" + e.getMessage());
    }
    return emitMongoDB(jsonNode);
  }

  private static String emitMongoDB(JsonNode rootNode) {
    String operator = "";
    try {
      operator = rootNode.fieldNames().next();
    } catch (NoSuchElementException e) {
      throw new ShepardParserException("error in parsing" + e.getMessage());
    }

    if (opAttributes.contains(operator)) {
      return emitPrimitiveClause(rootNode);
    } else if (booleanOperators.contains(operator)) {
      return emitComplexClause(rootNode, operator);
    } else {
      throw new ShepardParserException("unknown operator: " + operator);
    }
  }

  private static String emitOperatorString(JsonNode node) {
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

  private static String emitComplexClause(JsonNode node, String operator) {
    if (operator.equals(Constants.JSON_NOT)) {
      return emitNegatedClause(node.get(Constants.JSON_NOT));
    } else {
      return emitMultaryClause(node, operator);
    }
  }

  private static String emitNegatedClause(JsonNode node) {
    String operator = node.fieldNames().next();
    if (opAttributes.contains(operator)) {
      return emitNegatedPrimitiveClause(node);
    } else if (booleanOperators.contains(operator)) {
      return emitNegatedComplexClause(node, operator);
    } else {
      throw new ShepardParserException("unknown operator: " + operator);
    }
  }

  private static String emitNegatedComplexClause(JsonNode node, String operator) {
    if (operator.equals(Constants.JSON_NOT)) return emitMongoDB(node.get(Constants.JSON_NOT));
    else return emitNegatedMultaryClause(node, operator);
  }

  private static String emitNegatedPrimitiveClause(JsonNode node) {
    String ret = "";
    ret = ret + node.get(Constants.OP_PROPERTY).textValue() + ": {";
    ret = ret + "$not: {";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + ": ";
    ret = ret + node.get(Constants.OP_VALUE) + "}}";
    return ret;
  }

  private static String emitPrimitiveClause(JsonNode node) {
    String ret = "";
    String property = node.get(Constants.OP_PROPERTY).textValue();
    ret = ret + property + ": {";
    ret = ret + emitOperatorString(node.get(Constants.OP_OPERATOR)) + ": ";
    ret = ret + node.get(Constants.OP_VALUE);
    ret = ret + "}";
    return ret;
  }

  private static String emitMultaryClause(JsonNode node, String operator) {
    String ret = "";
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    ret = ret + emitBooleanOperator(operator) + " [{";
    String firstArgument = emitMongoDB(argumentsArray.next());
    ret = ret + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + "}, {" + emitMongoDB(argumentsArray.next());
    }
    ret = ret + "}]";
    return ret;
  }

  private static String emitNegatedMultaryClause(JsonNode node, String operator) {
    String ret = "";
    Iterator<JsonNode> argumentsArray = node.get(operator).elements();
    ret = ret + emitNegatedBooleanOperator(operator) + " [{";
    String firstArgument = emitNegatedClause(argumentsArray.next());
    ret = ret + firstArgument;
    while (argumentsArray.hasNext()) {
      ret = ret + "}, {" + emitNegatedClause(argumentsArray.next());
    }
    ret = ret + "}]";
    return ret;
  }

  private static String emitBooleanOperator(String operator) {
    return switch (operator) {
      case Constants.JSON_AND -> "$and:";
      case Constants.JSON_OR -> "$or:";
      default -> throw new ShepardParserException("unknown operator: " + operator);
    };
  }

  private static String emitNegatedBooleanOperator(String operator) {
    return switch (operator) {
      case Constants.JSON_AND -> "$or:";
      case Constants.JSON_OR -> "$and:";
      default -> throw new ShepardParserException("unknown operator: " + operator);
    };
  }
}
