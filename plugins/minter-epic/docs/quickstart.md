# Quickstart: Publish with ePIC Handles

shepard can mint persistent handles via the ePIC Handle Service so your datasets
get citable, dereferenceable PIDs (e.g. `https://hdl.handle.net/21.T11148/...`).

## Before you start

- You need an ePIC consortium membership (or access to a Helmholtz ePIC deployment).
- Your operator must have configured and enabled the ePIC minter plugin.
  Check with: `shepard-admin minters epic status`.

## Publish a data object

1. Open the data object you want to publish.
2. Click **Publish** → **Mint PID**.
3. shepard calls the ePIC API and attaches a handle URL to the publication record.
4. The handle resolves to your data object's landing page via `hdl.handle.net`.

## Check if the minter is ready

```
shepard-admin minters epic status
shepard-admin minters epic test-connection
```

If `reachable=false`, contact your instance administrator.

## More information

- [Reference: ePIC Minter](../reference/minter-epic.md)
- [Operator install guide](../install/minter-epic.md)
