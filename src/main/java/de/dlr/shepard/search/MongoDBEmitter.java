package de.dlr.shepard.search;

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.util.Constants;

public class MongoDBEmitter {

	private static final List<String> booleanOperators = List.of(Constants.JSON_AND, Constants.JSON_OR,
			Constants.JSON_NOT);

	private MongoDBEmitter() {
	}

	public static String emitMongoDB(String query) {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			jsonNode = objectMapper.readValue(query, JsonNode.class);
		} catch (Exception e) {
			throw new ShepardParserException("error while reading JSON\n" + e.getMessage());
		}
		return emitMongoDB(jsonNode);
	}

	private static String emitMongoDB(JsonNode rootNode) {
		String op = rootNode.fieldNames().next();
		if (Constants.OP_PROPERTY.equals(op) || Constants.OP_VALUE.equals(op) || Constants.OP_OPERATOR.equals(op)) {
			return emitPrimitiveClause(rootNode);
		}
		return emitComplexClause(rootNode, op);
	}

	private static String emitOperatorString(JsonNode node) {
		String operator = node.textValue();
		switch (operator) {
		case Constants.JSON_GT:
			return "$gt";
		case Constants.JSON_LT:
			return "$lt";
		case Constants.JSON_GE:
			return "$gte";
		case Constants.JSON_LE:
			return "$lte";
		case Constants.JSON_EQ:
			return "$eq";
		case Constants.JSON_IN:
			return "$in";
		case Constants.JSON_NE:
			return "$ne";
		default:
			throw new ShepardParserException("unknown comparison operator " + operator);
		}
	}

	private static String emitComplexClause(JsonNode node, String operator) {
		if (!booleanOperators.contains(operator))
			throw new ShepardParserException("unknown boolean operator: " + operator);
		if (operator.equals(Constants.JSON_NOT))
			return emitNegatedClause(node.get(Constants.JSON_NOT));
		else
			return emitMultaryClause(node, operator);
	}

	private static String emitNegatedClause(JsonNode node) {
		String op = node.fieldNames().next();
		if (op.equals(Constants.OP_PROPERTY) || op.equals(Constants.OP_VALUE) || op.equals(Constants.OP_OPERATOR)) {
			return emitNegatedPrimitiveClause(node);
		}
		return emitNegatedComplexClause(node, op);
	}

	private static String emitNegatedComplexClause(JsonNode node, String operator) {
		if (operator.equals(Constants.JSON_NOT))
			return emitMongoDB(node.get(Constants.JSON_NOT));
		else
			return emitNegatedMultaryClause(node, operator);
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
		switch (operator) {
		case Constants.JSON_AND:
			return "$and:";
		case Constants.JSON_OR:
			return "$or:";
		default:
			throw new ShepardParserException("unknown operator: " + operator);
		}
	}

	private static String emitNegatedBooleanOperator(String operator) {
		switch (operator) {
		case Constants.JSON_AND:
			return "$or:";
		case Constants.JSON_OR:
			return "$and:";
		default:
			throw new ShepardParserException("unknown operator: " + operator);
		}
	}

}
