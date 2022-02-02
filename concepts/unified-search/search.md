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

> tbd

## MetaData

> needs MetaData Reference, tbd

## Organizational Elements

Query collections, data objects and references

### Query objects

The query object consists of logical objects and matching objects.
Matching objects can contain the following attributes:

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
- `gt` (*greater than*, has `value`)
- `lt` (*lower than*, has `value`)
- `ge` (*greater or equal*, has `value`)
- `le` (*lower or equal*, has `value`)
- `eq` (*equals*, has `value`)
- `contains` (*contains*, has `value`)

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
      "value": "b",
      "operator": "eq"
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
