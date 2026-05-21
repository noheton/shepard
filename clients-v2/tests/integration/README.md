# Integration tests — shepard /v2/ API

These tests exercise the live `/v2/` REST API against a running shepard instance.

## Prerequisites

```sh
# Install the client package with integration extras
pip install -e clients-v2/python-kiota[integration]
```

## Running

```sh
export SHEPARD_URL=https://shepard.example.com
export SHEPARD_API_KEY=<bearer-token>

pytest clients-v2/tests/integration/ -v
```

When either env var is absent every test is **skipped**, not failed — safe to
run in a plain `pytest` sweep.

## Kiota client fixture

`conftest.py` also wires up a `v2_client` fixture backed by the Kiota
`HttpxRequestAdapter`. It skips automatically when the generated client
(`shepard_v2/`) is empty. Once `make -C clients-v2 generate-python` has run,
this fixture resolves to a fully-typed `ShepardV2Client` instance.

## Test organisation

| File | Endpoints covered |
|------|------------------|
| `test_instance.py` | `GET /v2/instance/capabilities`, `GET /v2/instance/identity` |
| `test_me.py` | `GET /v2/users/me` |
| `test_collections.py` | `GET/POST/PATCH/DELETE /v2/collections` |
| `test_data_objects.py` | `GET/POST/PATCH/DELETE /v2/collections/{appId}/data-objects` |
