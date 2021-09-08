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
