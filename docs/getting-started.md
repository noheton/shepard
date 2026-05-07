---
layout: default
title: Getting started
description: A 14-line Python quickstart against a running shepard instance.
---

This walks through creating a Collection, creating a DataObject, attaching a
child and a successor, and reading the data back — using the auto-generated
Python client.

You will need:

- A reachable shepard backend (its base URL ends in `/shepard/api`).
- An API key from an account on that instance (issued via the user profile
  in the frontend, or via `POST /shepard/api/apiKeys` if you have a token).

## Install the client

The Python client is published to the GitLab Package Registry. Until the
release feed is on PyPI proper, install with `--index-url`:

```bash
pip install shepard-client \
  --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple
```

## The 14-line quickstart

```python
from shepard_client.api_client import ApiClient
from shepard_client.configuration import Configuration
from shepard_client.api.collection_api import CollectionApi
from shepard_client.api.data_object_api import DataObjectApi
from shepard_client.models.collection import Collection
from shepard_client.models.data_object import DataObject

HOST   = "https://backend.shepard.example.com/shepard/api"
APIKEY = "your-api-key"

conf   = Configuration(host=HOST, api_key={"apikey": APIKEY})
client = ApiClient(configuration=conf)

# 1. List collections you can see.
collection_api = CollectionApi(client)
collections    = collection_api.get_all_collections()

# 2. Create a Collection.
created_collection = collection_api.create_collection(
    collection=Collection(
        name="MyFirstCollection",
        description="This is my first collection",
        attributes={"attribute1": "firstAttribute", "attribute2": "secondAttribute"},
    )
)
assert created_collection.id is not None

# 3. Create a DataObject in it.
dataobject_api = DataObjectApi(client)
created_dataobject = dataobject_api.create_data_object(
    collection_id=created_collection.id,
    data_object=DataObject(name="MyFirstDataObject", description="This is my first data object"),
)
assert created_dataobject.id is not None

# 4. Add a child DataObject.
created_child = dataobject_api.create_data_object(
    collection_id=created_collection.id,
    data_object=DataObject(name="Child", description="This is my second data object",
                           parentId=created_dataobject.id),
)
assert created_child.id is not None

# 5. Add a successor to the child (sibling under the same parent).
dataobject_api.create_data_object(
    collection_id=created_collection.id,
    data_object=DataObject(name="Successor", description="This is my third data object",
                           parentId=created_dataobject.id,
                           predecessorIds=[created_child.id]),
)

# 6. Read the parent back to see the new structure.
print(dataobject_api.get_data_object(
    collection_id=created_collection.id,
    data_object_id=created_dataobject.id,
))
```

## What's coming — the convenience client

A `shepard-py` wrapper is **planned** that collapses the prelude (host + key +
`Configuration` + `ApiClient`) into a single `Shepard(host, api_key=...)`
construction, plus methods that map Pythonic names onto the OpenAPI clients.
The corresponding `shepard-ts` wrapper is also planned. **These wrappers are
design-only** at this snapshot date — see the convenience-clients design note
in `aidocs/` (forthcoming) for the proposed API surface. Until then, the
auto-generated `shepard-client` shown above is the supported path.

## Next steps

- Read [User guide]({{ '/user-guide' | relative_url }}) for the full set of
  References and the permission model.
- Open the live API explorer at `https://your-host/shepard/doc/swagger-ui`
  (path verified from `application.properties` — `quarkus.http.non-application-root-path=/shepard/doc`,
  `quarkus.swagger-ui.always-include=true`).
