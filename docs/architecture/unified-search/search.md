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
```sql
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

#### Alternative query document

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
      <stringified? Query Object>

    },
    "queryType": "timeseries"
  }
}
```
The following logical operators are supported:

- `and` (has no `properties` but `values`)
- `or` (has no `properties` but `values`)
- `gt` (*greater than*, has `value`)
- `lt` (*lower than*, has `value`)
- `ge` (*greater or equal*, has `value`)
- `le` (*lower or equal*, has `value`)
- `eq` (*equals*, has `value`)
- `contains` (*contains*, has `value`)


This query looks for timeseries (within scope) that exceed 400°C in the TemperaturePly series and the robot is on path (VC_ONPATH > 0) 
```json
{ # implicit and
        { "symbolic_name": 'TemperaturPly',
          "operator" : "gt", # see https://docs.influxdata.com/influxdb/v1.8/query_language/explore-data/#supported-operators
          "value": "400"  
        },
        { "symbolic_name": 'VC_ON_PATH',
          "operator" : ">", # see https://docs.influxdata.com/influxdb/v1.8/query_language/explore-data/#supported-operators
          "value": "0"  
        }
}
```
This kind of query is not possible with InfluxQL -> https://community.influxdata.com/t/how-can-i-combine-two-different-measurements/6936/3

>Unfortunately, there is **no way to perform cross-measurement** math or grouping with influxql. All data must be under a single measurement to query it together. This issue has been raised. With Flux you will be able to do this.

_Simple_ queries like this would still work: 
{
        { "symbolic_name": 'TemperaturPly', # replace symbolic_name with property?
          "operator" : "gt", # see https://docs.influxdata.com/influxdb/v1.8/query_language/explore-data/#supported-operators
          "value": "400"  
        }
}

```sql
SELECT count("value") FROM "7e936d47-806c-4009-8dbb-646087c29a82"."autogen"."celsius" -- timeseries container taken from reference
WHERE time < '2016-07-31T20:07:00Z' AND time > '2016-07-31T23:07:17Z' --maybe not consistant, but taken from reference in scope
AND "symbolic_name"='TemperaturPly' 
AND "value" >= 400 
```

- [ ] Open issue: support aggregation functions like mean, median etc 

## MetaData

> needs MetaData Reference, tbd

## Organizational Elements

Query collections, data objects and references

### Query objects

The query object consists of logical objects and matching objects. Matching objects can contain the following attributes:

- `name` (String)
- `description` (String)
- `createdAt` (Date)
- `createdBy` (String)
- `updatedAt` (Date)
- `updatedBy` (String)
- `attributes` (Map[String, String])

The following logical objects are supported:

- `not` (has one `clause`)
- `and` (has a list of `clauses`)
- `or` (has a list of `clauses`)
- `xor` (has a list of `clauses`)
- `gt` (_greater than_, has `value`)
- `lt` (_lower than_, has `value`)
- `ge` (_greater or equal_, has `value`)
- `le` (_lower or equal_, has `value`)
- `eq` (_equals_, has `value`)
- `contains` (_contains_, has `value`)
- `in` (_in_, has a list of `values`)

```json
{
  "AND": [
    {
      "property": "name",
      "value": "MyName",
      "operator": "eq"
    },
    {
      "property": "number",
      "value": 123,
      "operator": "le"
    },
    {
      "property": "createdBy",
      "value": "haas_tb",
      "operator": "eq"
    },
    {
      "property": "attributes.a",
      "value": [1, 2, 3],
      "operator": "in"
    },
    {
      "OR": [
        {
          "property": "createdAt",
          "value": "2021-05-12",
          "operator": "gt"
        },
        {
          "property": "attributes.b",
          "value": "abc",
          "operator": "contains"
        }
      ]
    },
    {
      "NOT": {
        "property": "attributes.b",
        "value": "abc",
        "operator": "contains"
      }
    }
  ]
}
```

### Procedure

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
      "query": "<json formatted query string (see above)>"
    },
    "queryType": "organizational"
  }
}
```

2. Find all relevant elements (here the nodes with IDs 1, 2 and 3)
3. Build query

```cypher
MATCH (n)-[:createdBy]-(c:User) WHERE ID(n) in [1,2,3]
  AND c.username = "haas_tb"
  AND n.name = "MyName"
  AND n.description CONTAINS "Hallo Welt"
  AND n.`attributes.a` = "b"
  AND (
    n.createdAt > date("2021-05-12") OR n.`attributes.b` CONTAINS "abc"
  )
RETURN n
```

4. Query neo4j (3)
5. Return results

```json
{
  "resultSet": [
    {
      "collectionId": 123,
      "dataObjectId": 456,
      "referenceId": null
    }
  ],
  "search": {
    "query": {
      "query": "<>"
    },
    "queryType": "organizational"
  }
}
```

## User

1. Receiving search query via GET request `/search/users`
2. Possible query parameters are `username`, `firstName`, `lastName`, and `email`
3. Build query to enable regular expressions

```cypher
MATCH (u:User) WHERE u.firstName =~ "John" AND u.lastName =~ "Doe" RETURN u
```

5. Query neo4j (2)
6. Return results

```json
[
  {
    "username": "string",
    "firstName": "string",
    "lastName": "string",
    "email": "string",
    "subscriptionIds": [0],
    "apiKeyIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
  }
]
```
