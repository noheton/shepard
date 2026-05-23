---
layout: default
title: Publish a DataObject or Collection
permalink: /help/publish-data-object/
audience: user
---
# Publish a DataObject or Collection

A **publish** in shepard mints a persistent identifier (PID) for one
of your entities — a [DataObject](/reference/data-object/) or a
[Collection](/reference/collection/) — and attaches a small public
metadata record so any HMC PID resolver can find it again.

This is the casual-task pathway for the HMC **Kernel Information
Profile** (KIP) integration ([reference](/reference/publish-and-pids/)).
The aim is: a researcher writes a paper, drops the PID in the
paper, and any HMC tool walking that PID lands on the shepard
entity it points at — without prior knowledge of which shepard
instance it lives on.

## Step 1: click Publish

The fastest path is the web UI. Open the entity in shepard's
frontend — any [Collection](/reference/collection/) or
[DataObject](/reference/data-object/) detail pane carries a
**Publish** button at the top, next to the title and metadata
block.

If you don't see the button, you don't have **Writer** or
**Manager** permission on the entity — ask an owner / manager to
publish it for you, or to grant you the permission.

Click **Publish**, pick a licence in the modal that opens
(`CC-BY-4.0` is a sensible default for openly-shared research
data), and confirm. The freshly-minted PID lands in a snackbar
at the bottom of the screen with two copy buttons:

- **Copy resolver URL** — the public URL HMC PID resolvers walk to
  find the entity. Paste this into your dataset citation if the
  citation venue prefers a resolvable URL.
- **Copy PID** — the bare persistent identifier (e.g.
  `mock:shepard:data-objects:01HF...:174…`). Paste this into a
  paper if your citation style wants the PID itself.

The PID is permanent. You can update the entity later (rename it,
add data references, edit attributes) without changing the PID —
a published shepard entity is the same kind of mutable record it
always was, just with a citable handle attached. Republishing the
same entity returns the same PID (the API is idempotent); use
`?force=true` from the REST surface if you really need a fresh PID
(e.g. a major revision you want to address separately).

For the full mechanics — REST shape, errors, the resolver
endpoint — see [Publishing and PIDs (reference)](/reference/publish-and-pids/).

## When to publish

| You want to … | Publish? |
|---|---|
| Cite the dataset in a paper / report / talk. | **Yes** — the PID is the stable handle. |
| Share with a colleague on the same shepard instance. | No — share a permalink to the entity instead. |
| Make the dataset findable in Helmholtz Unhide. | Yes (and configure the Unhide harvest plugin in `aidocs/67` once it ships). |
| Tag a "this run is final" snapshot. | Pair with a [version snapshot](/reference/snapshots/) once V2 ships; today the PID alone is the marker. |

## Publish a DataObject

```
POST /v2/data-objects/{appId}/publish
```

Authentication: standard shepard JWT or API key. You need **Writer
or Manager** permission on the DataObject (or its parent Collection
— Manager on the Collection covers everything in it).

A typical response:

```json
{
  "appId": "01HF6N3R-pub-row",
  "pid": "mock:shepard:data-objects:01HF...:1747000000000",
  "mintedAt": "2026-05-13T08:11:00Z",
  "minterId": "mock",
  "resolverUrl": "https://shepard.example.dlr.de/v2/.well-known/kip/mock:shepard:data-objects:01HF...:1747000000000",
  "publishedBy": "alice",
  "entityKind": "data-objects",
  "entityAppId": "01HF..."
}
```

The `pid` field is the persistent identifier — copy that into your
paper / dataset citation. The `resolverUrl` is what a casual tool
walking the PID can hit without authentication to retrieve the
public KIP record.

## Publish a Collection

Same shape:

```
POST /v2/collections/{appId}/publish
```

The PID then points at the whole Collection rather than a single
DataObject — useful when "the campaign" is the citable unit, not
one TR-004 run.

## Re-publishing (idempotency + force)

A second POST on an already-published entity returns the existing
Publication row — same PID, no fresh mint. That's the right shape
for the common case ("ship the script that publishes every entity
in this Collection, re-run it tomorrow"): re-publishing is a no-op.

If you really want a fresh PID (e.g. a major revision that you want
addressable separately), pass `?force=true`:

```
POST /v2/data-objects/{appId}/publish?force=true
```

That mints a new PID and attaches it as an additional `:Publication`
row. The most recent row is "current" by KIP convention. Older PIDs
keep resolving — KIP records are append-only.

## What others see (the public resolver)

The PID can be dereferenced **without authentication** at:

```
GET /v2/.well-known/kip/{pid-suffix}
```

…where `{pid-suffix}` is the verbatim string from the `pid` field.
Mock-shaped PIDs carry colons; Handle / DOI PIDs typically carry a
slash — both work in the URL without encoding.

The response is a small JSON-LD-flavoured KIP record:

```json
{
  "@context": "https://hmc.helmholtz.de/kip/v1",
  "id": "mock:shepard:data-objects:01HF...:1747000000000",
  "kernelInformationProfile": {
    "id": "mock:shepard:data-objects:01HF...:1747000000000",
    "landingPage": "https://shepard.example.dlr.de/v2/data-objects/01HF...",
    "digitalObjectType": "http://shepard.dlr.de/types/dlr:DataObject",
    "dateCreated": "2026-05-13T08:11:00Z",
    "dateModified": "2026-05-13T08:11:00Z",
    "rightsHolder": "alice",
    "license": null
  }
}
```

The `landingPage` URL is the **entity** URL — that one *does*
require authentication (your shepard ACLs apply). The KIP record
itself is findability metadata, not entity payload, so the resolver
endpoint stays public by design.

## Errors

| Status | What it means | What to do |
|---|---|---|
| 401 | No auth on the publish call. | Send a JWT / API key. |
| 403 | You don't have Writer or Manager. | Ask an owner / manager of the entity. |
| 404 (`publish.kind.unsupported`) | URL segment isn't one shepard publishes (KIP1a only knows `data-objects` + `collections`). | Use a supported kind; bundles / files / lab-journal entries come in later KIP slices. |
| 404 (`publish.entity.wrong-kind` / no body) | The appId doesn't exist (or is a different kind). | Confirm the appId and that it's the type you expected. |
| 404 (`kip.pid.not-found`) | Resolver hit, but no Publication with that PID exists at this instance. | The PID belongs to a different shepard, or the row was hard-deleted (KIP records are append-only — this is exceptional). |
| 500 (`publish.minter.failed`) | The active Minter (ePIC / DataCite once KIP1c/d ship) returned an error. | Read the `detail` field — it's the upstream's reason. Retry, or check minter credentials. |

## Further reading

- [Reference: Publishing and PIDs](/reference/publish-and-pids/) — full schema, every endpoint, every config knob.
- [HMC KIP background](https://doi.org/10.3289/HMC_publ_03) — the published spec the shape implements.
- [Design doc: `aidocs/66`](https://github.com/noheton/shepard/blob/main/aidocs/66-hmc-kip-integration.md) — the full design with ePIC + DataCite plugin shapes (KIP1c/d, queued).
