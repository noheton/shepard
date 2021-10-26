# Search Concept

## Structured Data

Query documents using [native mongoDB mechanics](https://docs.mongodb.com/manual/tutorial/query-documents/)

1. Receiving search query via POST request

```json
{
  "scopes": [
    {
      "collectionId": 123,
      "dataObjectId": 456,
      "traversalRules": ["children"]
    }
  ],
  "search": {
    "query": {
      "query": "{ status: 'A', qty: { $lt: 30 } }"
    },
    "queryType": "structuredData"
  }
}
```

2. Find all relevant references (children of dataObject with id 456)
3. Find references containers
4. Build query

```txt
db.inventory.find( {"_id": $in: [ list of containers from 3 ] (implicit AND by), <user query>})
```

5. Query mongoDB (4)
6. Return results

```json
{
  "resultSet": [
    {
      "collectionId": 123,
      "dataObjectId": 456,
      "referenceId": 789
    }
  ],
  "search": {
    "query": {
      "query": "{ status: 'A', qty: { $lt: 30 } }"
    },
    "queryType": "structuredData"
  }
}
```

## Files

> tbd

## Timeseries
Not in scope: if a series named XYZ exists --> search in MetaData / OrgaElements

Here search **in payload**:
Comparing the first structured data use case:
There might be value in having the possibility to query timeseries. 
An example might be to query if a specific timeseries contains values above/below a certain threshold.

This query for example asks if the series "TemperaturPly" exceeds 400°C. (in this case globally with no "time" restriction, which would be given by the reference)
```
SELECT count("value") FROM "7e936d47-806c-4009-8dbb-646087c29a82"."autogen"."celsius" WHERE "value" >= 400 AND "symbolic_name"='TemperaturPly'
```
Basically we have to "inject" part of the WHERE clause (similar to structured data) and return a count() of results.
If this is greater than zero we have a "match".

First idea for a possible query in unified form:
```json
{
  "scopes": [
    {
      "collectionId": 123,
      "dataObjectId": 456,
      "traversalRules": ["children"]
    }
  ],
  "search": {
    "query": {
      "query": //a stringified json object? 
        "{ "symbolic_names": ['TemperaturPly'],
          "operator" : ">", # see https://docs.influxdata.com/influxdb/v1.8/query_language/explore-data/#supported-operators
          "value": "400"  
        }" 
    },
    "queryType": "timeseries"
  }
}
```
Open issues:
* maybe also a list of "querys" to allow more complex querys?
* also how to allow more "complex" queryies like "400 > value AND value < 500"?
* maybe represent the query object in polish notation: (probably easy to parse), maybe support "or" as well?
```json
{
  "and": [
      { 
      "symbolic_name": "TemperaturPly",
      "operator" : ">", 
      "value": 400  
      },
      {
      "symbolic_name": "TemperaturPly",
      "operator" : "<", 
      "value": 500  
      }
    ]
}
```

## MetaData

> needs MetaData Reference, tbd
