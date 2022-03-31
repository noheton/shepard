package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardParserException;

public class MongoDBEmitterTest extends BaseTestCase {

	@Test
	public void queryEqTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$eq: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryNeTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "ne"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$ne: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryGtTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "gt"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$gt: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryLtTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "lt"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$lt: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryGeTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "ge"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$gte: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryLeTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "le"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$lte: \"MyName\"}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryInTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
				{
				  "property": "name",
				  "value": ["1","2"],
				  "operator": "in"
				}
				""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$in: [\"1\",\"2\"]}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryNotTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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
								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$not: {$eq: [\"1\",\"2\"]}}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryAndTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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

								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "$and: [{name: {$eq: [\"1\",\"2\"]}}, {number: {$eq: 4}}]";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryOrTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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

								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "$or: [{name: {$eq: [\"1\",\"2\"]}}, {number: {$eq: 4}}]";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryNotNotTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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
								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "name: {$eq: [\"1\",\"2\"]}";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryNotOrTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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

								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "$and: [{name: {$not: {$eq: [\"1\",\"2\"]}}}, {number: {$not: {$eq: 4}}}]";
		assertEquals(expected, mongoDBQuery);
	}

	@Test
	public void queryNotAndTest() throws ShepardParserException {
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		String searchBodyQuery = """
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

								""";
		String mongoDBQuery = MongoDBEmitter.emitMongoDB(searchBodyQuery);
		String expected = "$or: [{name: {$not: {$eq: [\"1\",\"2\"]}}}, {number: {$not: {$eq: 4}}}]";
		assertEquals(expected, mongoDBQuery);
	}

}
