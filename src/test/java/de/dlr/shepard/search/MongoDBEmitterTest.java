package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardParserException;

public class MongoDBEmitterTest extends BaseTestCase {

	private static Stream<Arguments> queryTest() {
		var queryEq = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""", "name: {$eq: \"MyName\"}");
		var queryNe = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "ne"
				}
				""", "name: {$ne: \"MyName\"}");
		var queryGt = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "gt"
				}
				""", "name: {$gt: \"MyName\"}");
		var queryLt = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "lt"
				}
				""", "name: {$lt: \"MyName\"}");
		var queryGe = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "ge"
				}
				""", "name: {$gte: \"MyName\"}");
		var queryLe = Arguments.of("""
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "le"
				}
				""", "name: {$lte: \"MyName\"}");
		var queryIn = Arguments.of("""
				{
				  "property": "name",
				  "value": ["1","2"],
				  "operator": "in"
				}
				""", "name: {$in: [\"1\",\"2\"]}");
		var queryNot = Arguments.of("""
				{
				   "NOT":{
				      "property":"name",
				      "value":[
				         "1",
				         "2"
				      ],
				      "operator":"eq"
				   }
				}
				""", "name: {$not: {$eq: [\"1\",\"2\"]}}");
		var queryAnd = Arguments.of("""
				{
				   "AND":[
				      {
				         "property":"name",
				         "value":[
				            "1",
				            "2"
				         ],
				         "operator":"eq"
				      },
				      {
				         "property":"number",
				         "value":4,
				         "operator":"eq"
				      }
				   ]
				}
				""", "$and: [{name: {$eq: [\"1\",\"2\"]}}, {number: {$eq: 4}}]");
		var queryOr = Arguments.of("""
				{
				   "OR":[
				      {
				         "property":"name",
				         "value":[
				            "1",
				            "2"
				         ],
				         "operator":"eq"
				      },
				      {
				         "property":"number",
				         "value":4,
				         "operator":"eq"
				      }
				   ]
				}
				""", "$or: [{name: {$eq: [\"1\",\"2\"]}}, {number: {$eq: 4}}]");
		var queryNotNot = Arguments.of("""
				{
				   "NOT":{
				      "NOT":{
				         "property":"name",
				         "value":[
				            "1",
				            "2"
				         ],
				         "operator":"eq"
				      }
				   }
				}
				""", "name: {$eq: [\"1\",\"2\"]}");
		var queryNotOr = Arguments.of("""
				{
				   "NOT":{
				      "OR":[
				         {
				            "property":"name",
				            "value":[
				               "1",
				               "2"
				            ],
				            "operator":"eq"
				         },
				         {
				            "property":"number",
				            "value":4,
				            "operator":"eq"
				         }
				      ]
				   }
				}
				""", "$and: [{name: {$not: {$eq: [\"1\",\"2\"]}}}, {number: {$not: {$eq: 4}}}]");
		var queryNotAnd = Arguments.of("""
				{
				   "NOT":{
				      "AND":[
				         {
				            "property":"name",
				            "value":[
				               "1",
				               "2"
				            ],
				            "operator":"eq"
				         },
				         {
				            "property":"number",
				            "value":4,
				            "operator":"eq"
				         }
				      ]
				   }
				}
				""", "$or: [{name: {$not: {$eq: [\"1\",\"2\"]}}}, {number: {$not: {$eq: 4}}}]");
		return Stream.of(queryEq, queryNe, queryGt, queryLt, queryGe, queryLe, queryIn, queryNot, queryAnd, queryOr,
				queryNotNot, queryNotOr, queryNotAnd);
	}

	@ParameterizedTest
	@MethodSource
	public void queryTest(String input, String expected) throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(input);
		assertEquals(expected, mongoDBQuery);
	}

}
