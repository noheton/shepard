# shepard — Python convenience client

Thin wrapper around the generated `shepard-client` package.
Collapses the 14-line connection prelude to three lines.

## Install

```bash
pip install shepard               # core (CRUD, pagination, error hierarchy)
pip install 'shepard[pandas]'     # + to_pandas()
pip install 'shepard[excel]'      # + to_pandas() + to_excel()
```

The `shepard-client` generated package must also be available:
```bash
pip install --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple \
    shepard-client==5.2.0
```

## Hello world

```python
import shepard
from shepard.models import Collection

sh = shepard.Client(
    host="https://backend.shepard.example.com/shepard/api",
    apikey="sk-...",
)
created = sh.collections.create_collection(
    collection=Collection(name="My Dataset", description="First experiment")
)
print(created.id)
```

## Pagination

```python
# Generator — lazy fetch, each page is one HTTP call:
for col in sh.collections.iter("get_all_collections"):
    print(col.name)

# Eager list (raises ValueError if > list_cap=10 000 items):
all_cols = sh.collections.list("get_all_collections")
```

## to_pandas

```python
df = sh.to_pandas(
    timeseries_id=42,
    container_id=7,
    start="2025-01-01T00:00:00Z",
    end="2025-06-01T00:00:00Z",
)
print(df.head())
```

## to_excel

```python
path = sh.to_excel(42, "/tmp/data.xlsx", container_id=7)
print(f"Written to {path}")
```

## Error handling

```python
from shepard import ShepardNotFound, ShepardForbidden

try:
    sh.collections.get_collection_by_id(collection_id=99999)
except ShepardNotFound:
    print("Collection not found")
except ShepardForbidden:
    print("No permission")
```

Full method signatures: see the generated
[shepard-client](https://gitlab.com/dlr-shepard/shepard) for every `*Api` class.
